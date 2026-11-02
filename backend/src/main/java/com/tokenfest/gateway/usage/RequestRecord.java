package com.tokenfest.gateway.usage;

import java.math.BigDecimal;
import java.time.Instant;

import com.tokenfest.gateway.domain.ProviderType;

/**
 * Everything saved about one proxied request.
 */
public record RequestRecord(Long apiKeyId, Long providerId, ProviderType apiFormat, String model, int statusCode,
		boolean streamed, Usage usage, BigDecimal cost, int latencyMs, int attempts, String errorMessage,
		String requestBody, String responseBody, Instant createdAt) {

	public boolean successful() {
		return statusCode >= 200 && statusCode < 300;
	}

}
