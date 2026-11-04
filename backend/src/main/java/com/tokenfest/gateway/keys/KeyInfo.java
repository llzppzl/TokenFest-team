package com.tokenfest.gateway.keys;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Read-only copy of an enabled API key, safe to cache and share between threads.
 *
 * @param allowedProviderIds empty means all providers
 */
public record KeyInfo(Long id, String name, BigDecimal monthlyBudget, Integer rateLimitPerMinute,
		Set<Long> allowedProviderIds) {
}
