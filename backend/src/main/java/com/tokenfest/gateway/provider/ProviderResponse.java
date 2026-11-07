package com.tokenfest.gateway.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.ProviderType;

/**
 * Provider as shown in the dashboard. The API key itself is never returned.
 */
public record ProviderResponse(Long id, String name, ProviderType type, String baseUrl, int priority,
		boolean enabled, boolean hasApiKey, List<ModelResponse> models, Instant createdAt, Instant updatedAt,
		int consecutiveFailures, boolean coolingDown) {

	public record ModelResponse(String modelName, BigDecimal inputPrice, BigDecimal outputPrice) {
	}

	static ProviderResponse from(Provider p) {
		return from(p, 0, false);
	}

	static ProviderResponse from(Provider p, int consecutiveFailures, boolean coolingDown) {
		List<ModelResponse> models = p.getModels()
			.stream()
			.map(m -> new ModelResponse(m.getModelName(), m.getInputPrice(), m.getOutputPrice()))
			.toList();
		return new ProviderResponse(p.getId(), p.getName(), p.getType(), p.getBaseUrl(), p.getPriority(),
				p.isEnabled(), p.getApiKeyEncrypted() != null, models, p.getCreatedAt(), p.getUpdatedAt(),
				consecutiveFailures, coolingDown);
	}

}
