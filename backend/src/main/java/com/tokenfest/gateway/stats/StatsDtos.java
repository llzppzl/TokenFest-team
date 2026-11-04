package com.tokenfest.gateway.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.tokenfest.gateway.domain.ProviderType;

/** Response types of the stats and request log API. */
public final class StatsDtos {

	private StatsDtos() {
	}

	public record Summary(LocalDate from, LocalDate to, BigDecimal totalCost, long requests, long errors,
			long inputTokens, long outputTokens, long activeKeys) {
	}

	/** Totals for one day and one group (a key, model or provider). */
	public record DailyRow(LocalDate date, String group, BigDecimal cost, long requests, long inputTokens,
			long outputTokens) {
	}

	public record RequestRow(Long id, Instant createdAt, Long keyId, String keyName, Long providerId,
			String providerName, ProviderType apiFormat, String model, int statusCode, boolean streamed,
			int inputTokens, int outputTokens, BigDecimal cost, int latencyMs, int attempts, String errorMessage) {
	}

	public record RequestDetail(RequestRow request, String requestBody, String responseBody) {
	}

	public record Page<T>(List<T> items, int page, int size, long total) {
	}

}
