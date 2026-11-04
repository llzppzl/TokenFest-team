package com.tokenfest.gateway.limits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.ConcurrentHashMap;

import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.usage.UsageRecordedEvent;
import com.tokenfest.gateway.usage.UsageRecorder;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps each key's spending for the current month in memory. Loaded from the database
 * once, then updated after every recorded request.
 *
 * <p>
 * Loading and updating both happen on the usage writer thread, so a request is never
 * counted twice or missed.
 */
@Component
public class SpendTracker {

	private record MonthSpend(YearMonth month, BigDecimal spent) {
	}

	private final ConcurrentHashMap<Long, MonthSpend> spend = new ConcurrentHashMap<>();

	private final UsageRecorder recorder;

	private final UsageDailyRepository usage;

	private final Clock clock;

	public SpendTracker(UsageRecorder recorder, UsageDailyRepository usage, Clock clock) {
		this.recorder = recorder;
		this.usage = usage;
		this.clock = clock;
	}

	/** Spending of the key in the current month. Blocking on the first call. */
	public BigDecimal spentThisMonth(Long keyId) {
		YearMonth month = YearMonth.now(clock);
		MonthSpend cached = spend.get(keyId);
		if (cached != null && cached.month().equals(month)) {
			return cached.spent();
		}
		try {
			return recorder.callOnWriter(() -> load(keyId, month));
		}
		catch (UsageRecorder.WriterBusyException e) {
			// Read the database directly but don't cache it: caching here could miss or
			// double-count a request the writer is saving at the same moment
			return usage.sumCost(keyId, month.atDay(1), month.atEndOfMonth());
		}
	}

	private BigDecimal load(Long keyId, YearMonth month) {
		MonthSpend cached = spend.get(keyId);
		if (cached != null && cached.month().equals(month)) {
			return cached.spent();
		}
		BigDecimal total = usage.sumCost(keyId, month.atDay(1), month.atEndOfMonth());
		spend.put(keyId, new MonthSpend(month, total));
		return total;
	}

	/** Runs before the alert check, which needs the new total. */
	@EventListener
	@Order(1)
	public void onUsageRecorded(UsageRecordedEvent event) {
		YearMonth month = YearMonth.from(LocalDate.ofInstant(event.createdAt(), clock.getZone()));
		spend.computeIfPresent(event.apiKeyId(),
				(id, current) -> current.month().equals(month)
						? new MonthSpend(month, current.spent().add(event.cost())) : current);
	}

	public void forget(Long keyId) {
		spend.remove(keyId);
	}

}
