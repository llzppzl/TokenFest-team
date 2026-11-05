package com.tokenfest.gateway.limits;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tokenfest.gateway.keys.KeyInfo;
import com.tokenfest.gateway.proxy.ProxyException;
import com.tokenfest.gateway.usage.Money;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Checks a key's monthly budget and rate limit before a request is forwarded.
 */
@Component
public class LimitChecker {

	/** One bucket per key and limit, so changing the limit starts a new bucket. */
	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.expireAfterAccess(Duration.ofMinutes(10))
		.maximumSize(100_000)
		.build();

	private final SpendTracker spendTracker;

	private final Clock clock;

	public LimitChecker(SpendTracker spendTracker, Clock clock) {
		this.spendTracker = spendTracker;
		this.clock = clock;
	}

	/** Throws a 429 {@link ProxyException} if a limit is reached. Blocking. */
	public void check(KeyInfo key) {
		if (key.monthlyBudget() != null) {
			BigDecimal spent = spendTracker.spentThisMonth(key.id());
			if (spent.compareTo(key.monthlyBudget()) >= 0) {
				throw new ProxyException(HttpStatus.TOO_MANY_REQUESTS, "budget_exceeded",
						"Monthly budget of " + Money.format(key.monthlyBudget()) + " is used up. It resets on "
								+ YearMonth.now(clock).plusMonths(1).atDay(1) + ".");
			}
		}
		Integer limit = key.rateLimitPerMinute();
		if (limit != null) {
			Bucket bucket = buckets.get(key.id() + ":" + limit, ignored -> newBucket(limit));
			ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
			if (!probe.isConsumed()) {
				long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999) / 1_000_000_000);
				throw new ProxyException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded",
						"Rate limit of " + limit + " requests per minute reached. Try again in " + seconds
								+ " seconds.",
						seconds);
			}
		}
	}

	/** {@code limit} requests per minute; the full amount comes back every minute. */
	private static Bucket newBucket(int limit) {
		Bandwidth bandwidth = Bandwidth.builder().capacity(limit).refillIntervally(limit, Duration.ofMinutes(1)).build();
		return Bucket.builder().addLimit(bandwidth).build();
	}

}
