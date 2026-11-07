package com.tokenfest.gateway.proxy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Remembers which providers are failing. After {@value #FAILURES_BEFORE_COOLDOWN} failures
 * in a row a provider "cools down" for {@code COOLDOWN}: it is tried last instead of
 * first, so requests don't wait on a provider that is down. One success resets it.
 */
@Component
public class ProviderHealth {

	static final int FAILURES_BEFORE_COOLDOWN = 3;

	static final Duration COOLDOWN = Duration.ofSeconds(30);

	public record Status(int consecutiveFailures, Instant coolingDownUntil) {

		public boolean coolingDown(Instant now) {
			return coolingDownUntil != null && now.isBefore(coolingDownUntil);
		}

	}

	private final ConcurrentHashMap<Long, Status> status = new ConcurrentHashMap<>();

	private final Clock clock;

	public ProviderHealth(Clock clock) {
		this.clock = clock;
	}

	public void recordSuccess(Long providerId) {
		status.remove(providerId);
	}

	public void recordFailure(Long providerId) {
		status.compute(providerId, (id, current) -> {
			int failures = (current == null ? 0 : current.consecutiveFailures()) + 1;
			Instant until = failures >= FAILURES_BEFORE_COOLDOWN ? Instant.now(clock).plus(COOLDOWN)
					: current != null ? current.coolingDownUntil() : null;
			return new Status(failures, until);
		});
	}

	public Status status(Long providerId) {
		return status.getOrDefault(providerId, new Status(0, null));
	}

	public boolean coolingDown(Long providerId) {
		return status(providerId).coolingDown(Instant.now(clock));
	}

	/** Same order, but providers that are cooling down move to the end. */
	public List<Route> order(List<Route> routes) {
		List<Route> healthy = new ArrayList<>();
		List<Route> cooling = new ArrayList<>();
		for (Route route : routes) {
			(coolingDown(route.providerId()) ? cooling : healthy).add(route);
		}
		healthy.addAll(cooling);
		return healthy;
	}

}
