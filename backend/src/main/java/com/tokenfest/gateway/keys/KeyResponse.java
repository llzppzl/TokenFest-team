package com.tokenfest.gateway.keys;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.domain.Provider;

public record KeyResponse(Long id, String name, String keyPrefix, BigDecimal monthlyBudget,
		Integer rateLimitPerMinute, boolean enabled, List<Long> allowedProviderIds, BigDecimal spentThisMonth,
		Instant createdAt, Instant lastUsedAt) {

	static KeyResponse from(ApiKey key, BigDecimal spentThisMonth) {
		List<Long> providerIds = key.getAllowedProviders().stream().map(Provider::getId).sorted().toList();
		return new KeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), key.getMonthlyBudget(),
				key.getRateLimitPerMinute(), key.isEnabled(), providerIds, spentThisMonth, key.getCreatedAt(),
				key.getLastUsedAt());
	}

	/** Returned only when a key is created; {@code apiKey} is the full key. */
	public record Created(KeyResponse key, String apiKey) {
	}

}
