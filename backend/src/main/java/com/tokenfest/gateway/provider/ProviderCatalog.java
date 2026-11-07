package com.tokenfest.gateway.provider;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.tokenfest.gateway.crypto.CryptoService;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.ProviderModel;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.repository.ProviderRepository;

import org.springframework.stereotype.Component;

/**
 * Cached list of enabled providers with decrypted keys, so routing needs no database call
 * or decryption per request. Cleared whenever a provider changes.
 */
@Component
public class ProviderCatalog {

	public record Price(BigDecimal input, BigDecimal output) {
	}

	/** Read-only copy of an enabled provider. */
	public record ProviderSnapshot(Long id, String name, ProviderType type, String baseUrl, String apiKey,
			Map<String, Price> models) {

		@Override
		public String toString() {
			return "ProviderSnapshot[" + name + "]"; // never print the API key
		}

	}

	private final LoadingCache<String, List<ProviderSnapshot>> cache;

	public ProviderCatalog(ProviderRepository providers, CryptoService crypto) {
		this.cache = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofSeconds(30))
			.build(ignored -> providers.findByEnabledTrueOrderByPriorityAscIdAsc()
				.stream()
				.map(p -> snapshot(p, crypto))
				.toList());
	}

	/** Enabled providers ordered by priority. Blocking on a cache miss. */
	public List<ProviderSnapshot> enabledProviders() {
		return cache.get("all");
	}

	public void invalidate() {
		cache.invalidateAll();
	}

	private static ProviderSnapshot snapshot(Provider p, CryptoService crypto) {
		Map<String, Price> models = new LinkedHashMap<>();
		for (ProviderModel m : p.getModels()) {
			models.put(m.getModelName(), new Price(m.getInputPrice(), m.getOutputPrice()));
		}
		return new ProviderSnapshot(p.getId(), p.getName(), p.getType(), p.getBaseUrl(),
				crypto.decrypt(p.getApiKeyEncrypted()), Map.copyOf(models));
	}

}
