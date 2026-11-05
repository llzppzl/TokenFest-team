package com.tokenfest.gateway.keys;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or updating an API key. Null budget or rate limit means no limit;
 * an empty provider list means all providers.
 */
public record KeyRequest(@NotBlank @Size(max = 100) String name, @PositiveOrZero BigDecimal monthlyBudget,
		@Positive Integer rateLimitPerMinute, List<Long> allowedProviderIds, Boolean enabled) {
}
