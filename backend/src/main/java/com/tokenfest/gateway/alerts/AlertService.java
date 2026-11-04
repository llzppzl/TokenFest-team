package com.tokenfest.gateway.alerts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tokenfest.gateway.alerts.AlertNotifier.Alert;
import com.tokenfest.gateway.alerts.AlertNotifier.DeliveryResult;
import com.tokenfest.gateway.domain.AlertEvent;
import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.limits.SpendTracker;
import com.tokenfest.gateway.repository.AlertEventRepository;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.settings.SettingsService;
import com.tokenfest.gateway.settings.SettingsService.AlertSettings;
import com.tokenfest.gateway.usage.UsageRecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Schedulers;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks budget thresholds after each recorded request and sends one alert per key,
 * month and threshold. The {@code alert_events} table remembers what was already sent, so
 * a restart never repeats an alert.
 */
@Service
public class AlertService {

	private static final Logger log = LoggerFactory.getLogger(AlertService.class);

	public record AlertEventResponse(Long id, Long keyId, String keyName, String month, int thresholdPercent,
			BigDecimal spent, BigDecimal budget, boolean delivered, String errorMessage, Instant createdAt) {
	}

	private final ApiKeyRepository apiKeys;

	private final AlertEventRepository alertEvents;

	private final SpendTracker spendTracker;

	private final SettingsService settings;

	private final AlertNotifier notifier;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public AlertService(ApiKeyRepository apiKeys, AlertEventRepository alertEvents, SpendTracker spendTracker,
			SettingsService settings, AlertNotifier notifier, TransactionTemplate transaction, Clock clock) {
		this.apiKeys = apiKeys;
		this.alertEvents = alertEvents;
		this.spendTracker = spendTracker;
		this.settings = settings;
		this.notifier = notifier;
		this.transaction = transaction;
		this.clock = clock;
	}

	/** Runs on the usage writer thread, after {@link SpendTracker} has added the cost. */
	@EventListener
	@Order(2)
	public void onUsageRecorded(UsageRecordedEvent event) {
		if (event.cost().signum() <= 0) {
			return;
		}
		ApiKey key = apiKeys.findById(event.apiKeyId()).orElse(null);
		if (key == null || key.getMonthlyBudget() == null || key.getMonthlyBudget().signum() <= 0) {
			return;
		}
		String month = YearMonth.from(LocalDate.ofInstant(event.createdAt(), clock.getZone())).toString();
		if (!month.equals(YearMonth.now(clock).toString())) {
			return;
		}
		BigDecimal spent = spendTracker.spentThisMonth(key.getId());
		BigDecimal budget = key.getMonthlyBudget();
		AlertSettings alertSettings = settings.alertSettings();

		List<AlertEvent> crossed = new ArrayList<>();
		for (int threshold : alertSettings.thresholds()) {
			boolean reached = spent.multiply(BigDecimal.valueOf(100))
				.compareTo(budget.multiply(BigDecimal.valueOf(threshold))) >= 0;
			if (reached && !alertEvents.existsByApiKeyIdAndBudgetMonthAndThresholdPercent(key.getId(), month,
					threshold)) {
				crossed.add(new AlertEvent(key.getId(), month, threshold, spent, budget));
			}
		}
		if (crossed.isEmpty()) {
			return;
		}
		List<AlertEvent> saved = transaction.execute(status -> alertEvents.saveAll(crossed));
		// If one request jumps over several thresholds, send only the highest one
		int highest = crossed.stream().mapToInt(AlertEvent::getThresholdPercent).max().orElseThrow();
		Alert alert = new Alert(key.getName(), month, highest, spent, budget, false);
		notifier.send(alert, alertSettings)
			.publishOn(Schedulers.boundedElastic())
			.subscribe(result -> markDelivered(saved, result),
					e -> log.error("Could not deliver budget alert for key {}", key.getId(), e));
	}

	private void markDelivered(List<AlertEvent> events, DeliveryResult result) {
		transaction.executeWithoutResult(status -> {
			for (AlertEvent event : events) {
				alertEvents.findById(event.getId()).ifPresent(e -> {
					e.setDelivered(result.anySent());
					e.setErrorMessage(result.errors());
				});
			}
		});
	}

	public List<AlertEventResponse> recent() {
		Map<Long, String> names = apiKeys.findAll()
			.stream()
			.collect(Collectors.toMap(ApiKey::getId, ApiKey::getName));
		return alertEvents.findTop100ByOrderByCreatedAtDesc()
			.stream()
			.map(e -> new AlertEventResponse(e.getId(), e.getApiKeyId(), names.getOrDefault(e.getApiKeyId(), "(deleted)"),
					e.getBudgetMonth(), e.getThresholdPercent(), e.getSpent(), e.getBudget(), e.isDelivered(),
					e.getErrorMessage(), e.getCreatedAt()))
			.toList();
	}

	/** Sends a test message to the configured channels. */
	public DeliveryResult sendTest() {
		Alert test = new Alert("test", YearMonth.now(clock).toString(), 80, BigDecimal.valueOf(80),
				BigDecimal.valueOf(100), true);
		return notifier.send(test, settings.alertSettings()).block();
	}

}
