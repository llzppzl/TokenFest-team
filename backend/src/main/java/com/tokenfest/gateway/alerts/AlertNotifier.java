package com.tokenfest.gateway.alerts;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.tokenfest.gateway.config.TokenFestProperties;
import com.tokenfest.gateway.settings.SettingsService.AlertSettings;
import com.tokenfest.gateway.usage.Money;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Sends budget alerts to the webhook and email recipients from the settings. Each channel
 * is tried up to 3 times with backoff. Never throws: the result says what happened.
 */
@Component
public class AlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);

	public static final String SENT = "sent";

	public static final String NOT_CONFIGURED = "not configured";

	/** What triggered the alert. */
	public record Alert(String keyName, String month, int thresholdPercent, BigDecimal spent, BigDecimal budget,
			boolean test) {

		String message() {
			String amounts = Money.format(spent) + " of " + Money.format(budget);
			if (test) {
				return "TokenFest test alert: alerts are working.";
			}
			if (thresholdPercent >= 100) {
				return "TokenFest: " + keyName + " has used up the monthly budget (" + amounts + " in " + month
						+ "). New requests are blocked until next month or until the budget is raised.";
			}
			return "TokenFest: " + keyName + " has used " + thresholdPercent + "% of the monthly budget (" + amounts
					+ " in " + month + ").";
		}

	}

	/** Outcome per channel: "sent", "not configured" or "failed: ...". */
	public record DeliveryResult(String webhook, String email) {

		boolean anySent() {
			return SENT.equals(webhook) || SENT.equals(email);
		}

		String errors() {
			StringBuilder sb = new StringBuilder();
			if (webhook.startsWith("failed")) {
				sb.append("webhook ").append(webhook);
			}
			if (email.startsWith("failed")) {
				sb.append(sb.isEmpty() ? "" : "; ").append("email ").append(email);
			}
			return sb.isEmpty() ? null : sb.toString();
		}

	}

	private final WebClient webClient;

	private final ObjectProvider<JavaMailSender> mailSender;

	private final String fromAddress;

	private final Duration retryBackoff;

	public AlertNotifier(ObjectProvider<JavaMailSender> mailSender, TokenFestProperties properties) {
		HttpClient httpClient = HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
			.responseTimeout(Duration.ofSeconds(10));
		this.webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
		this.mailSender = mailSender;
		this.fromAddress = properties.alerts().from();
		this.retryBackoff = properties.alerts().retryBackoff();
	}

	public boolean emailConfigured() {
		return mailSender.getIfAvailable() != null;
	}

	public Mono<DeliveryResult> send(Alert alert, AlertSettings settings) {
		return Mono.zip(webhook(alert, settings.webhookUrl()), email(alert, settings))
			.map(t -> new DeliveryResult(t.getT1(), t.getT2()));
	}

	private Mono<String> webhook(Alert alert, String url) {
		if (url == null || url.isBlank()) {
			return Mono.just(NOT_CONFIGURED);
		}
		// "text" is what Slack, Discord-compatible and Teams-style webhooks display
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("text", alert.message());
		payload.put("key", alert.keyName());
		payload.put("month", alert.month());
		payload.put("thresholdPercent", alert.thresholdPercent());
		payload.put("spent", alert.spent());
		payload.put("budget", alert.budget());
		payload.put("test", alert.test());
		return webClient.post()
			.uri(url)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(payload)
			.retrieve()
			.toBodilessEntity()
			.retryWhen(Retry.backoff(2, retryBackoff))
			.map(response -> SENT)
			.onErrorResume(e -> {
				log.warn("Webhook alert failed: {}", rootMessage(e));
				return Mono.just("failed: " + rootMessage(e));
			});
	}

	private Mono<String> email(Alert alert, AlertSettings settings) {
		if (settings.emails().isEmpty()) {
			return Mono.just(NOT_CONFIGURED);
		}
		JavaMailSender sender = mailSender.getIfAvailable();
		if (sender == null) {
			return Mono.just(NOT_CONFIGURED + " (no SMTP server)");
		}
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(fromAddress);
		mail.setTo(settings.emails().toArray(String[]::new));
		mail.setSubject(alert.test() ? "TokenFest test alert"
				: "TokenFest budget alert: " + alert.keyName() + " at " + alert.thresholdPercent() + "%");
		mail.setText(alert.message());
		return Mono.fromCallable(() -> {
			sender.send(mail);
			return SENT;
		})
			.subscribeOn(Schedulers.boundedElastic())
			.retryWhen(Retry.backoff(2, retryBackoff))
			.onErrorResume(e -> {
				log.warn("Email alert failed: {}", rootMessage(e));
				return Mono.just("failed: " + rootMessage(e));
			});
	}

	private static String rootMessage(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}

}
