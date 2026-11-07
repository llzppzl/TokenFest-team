package com.tokenfest.gateway.provider;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.tokenfest.gateway.config.AfterCommit;
import com.tokenfest.gateway.crypto.CryptoService;
import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.keys.ApiKeyService;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.ProviderModel;
import com.tokenfest.gateway.provider.ProviderRequest.ModelRequest;
import com.tokenfest.gateway.proxy.ProviderHealth;
import com.tokenfest.gateway.proxy.UpstreamClient;
import com.tokenfest.gateway.proxy.UpstreamClient.ConnectionTest;
import com.tokenfest.gateway.repository.ProviderRepository;
import com.tokenfest.gateway.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderService {

	private final ProviderRepository providers;

	private final CryptoService crypto;

	private final ProviderCatalog catalog;

	private final ProviderHealth health;

	private final UpstreamClient upstream;

	private final ApiKeyRepository apiKeys;

	private final ApiKeyService keyService;

	public ProviderService(ProviderRepository providers, CryptoService crypto, ProviderCatalog catalog,
			ProviderHealth health, UpstreamClient upstream, ApiKeyRepository apiKeys, ApiKeyService keyService) {
		this.apiKeys = apiKeys;
		this.keyService = keyService;
		this.providers = providers;
		this.crypto = crypto;
		this.catalog = catalog;
		this.health = health;
		this.upstream = upstream;
	}

	@Transactional(readOnly = true)
	public List<ProviderResponse> list() {
		return providers.findAllByOrderByPriorityAscIdAsc()
			.stream()
			.map(p -> ProviderResponse.from(p, health.status(p.getId()).consecutiveFailures(),
					health.coolingDown(p.getId())))
			.toList();
	}

	/** Calls the provider's model list with the saved key. Blocking. */
	public ConnectionTest test(Long id) {
		Provider provider = providers.findById(id).orElseThrow(() -> ApiException.notFound("Provider"));
		String key = crypto.decrypt(provider.getApiKeyEncrypted());
		return upstream.test(provider.getBaseUrl(), key, provider.getType()).block();
	}

	@Transactional
	public ProviderResponse create(ProviderRequest request) {
		if (request.apiKey() == null || request.apiKey().isBlank()) {
			throw ApiException.badRequest("apiKey is required");
		}
		if (providers.existsByName(request.name().trim())) {
			throw ApiException.conflict("A provider named '" + request.name().trim() + "' already exists");
		}
		Provider provider = new Provider();
		apply(provider, request);
		AfterCommit.run(catalog::invalidate);
		return ProviderResponse.from(providers.save(provider));
	}

	@Transactional
	public ProviderResponse update(Long id, ProviderRequest request) {
		Provider provider = providers.findWithModelsById(id).orElseThrow(() -> ApiException.notFound("Provider"));
		// The stored key is only reused for the same server; otherwise anyone who can edit
		// providers could send it to a server of their choice
		boolean keepsKey = request.apiKey() == null || request.apiKey().isBlank();
		if (keepsKey && !sameOrigin(provider.getBaseUrl(), request.baseUrl().trim())) {
			throw ApiException.badRequest("Enter the API key again when changing the provider's server");
		}
		String newName = request.name().trim();
		if (!newName.equals(provider.getName()) && providers.existsByName(newName)) {
			throw ApiException.conflict("A provider named '" + newName + "' already exists");
		}
		apply(provider, request);
		AfterCommit.run(catalog::invalidate);
		return ProviderResponse.from(providers.saveAndFlush(provider));
	}

	@Transactional
	public void delete(Long id) {
		if (!providers.existsById(id)) {
			throw ApiException.notFound("Provider");
		}
		// A key limited to only this provider would end up with an empty list, which means
		// "all providers". Make the admin decide first instead of silently widening access.
		List<String> onlyThisOne = apiKeys.findAllByOrderByIdAsc()
			.stream()
			.filter(k -> k.getAllowedProviders().size() == 1
					&& k.getAllowedProviders().iterator().next().getId().equals(id))
			.map(ApiKey::getName)
			.toList();
		if (!onlyThisOne.isEmpty()) {
			throw ApiException.conflict("These keys may only use this provider: " + String.join(", ", onlyThisOne)
					+ ". Change or disable them first.");
		}
		providers.deleteById(id);
		AfterCommit.run(() -> {
			catalog.invalidate();
			keyService.invalidateCache(); // cached keys hold the allowed provider ids
		});
	}

	private void apply(Provider provider, ProviderRequest request) {
		provider.setName(request.name().trim());
		provider.setType(request.type());
		provider.setBaseUrl(stripTrailingSlash(request.baseUrl().trim()));
		if (request.apiKey() != null && !request.apiKey().isBlank()) {
			provider.setApiKeyEncrypted(crypto.encrypt(request.apiKey().trim()));
		}
		provider.setPriority(request.priority() != null ? request.priority() : 100);
		provider.setEnabled(request.enabled() == null || request.enabled());
		updateModels(provider, request.models() != null ? request.models() : List.of());
	}

	/**
	 * Updates models in place by name. Removing and re-adding a model with the same name
	 * in one flush would break the unique constraint.
	 */
	private void updateModels(Provider provider, List<ModelRequest> requested) {
		Map<String, ModelRequest> byName = new HashMap<>();
		for (ModelRequest m : requested) {
			if (byName.put(m.modelName().trim(), m) != null) {
				throw ApiException.badRequest("Duplicate model: " + m.modelName().trim());
			}
		}
		provider.getModels().removeIf(existing -> !byName.containsKey(existing.getModelName()));
		for (ProviderModel existing : provider.getModels()) {
			ModelRequest m = byName.remove(existing.getModelName());
			existing.setInputPrice(m.inputPrice());
			existing.setOutputPrice(m.outputPrice());
		}
		byName.forEach((name, m) -> provider.addModel(new ProviderModel(name, m.inputPrice(), m.outputPrice())));
	}

	/** Same scheme, host and port. */
	static boolean sameOrigin(String a, String b) {
		try {
			URI x = URI.create(a);
			URI y = URI.create(b);
			return x.getScheme().equalsIgnoreCase(y.getScheme()) && x.getHost().equalsIgnoreCase(y.getHost())
					&& effectivePort(x) == effectivePort(y);
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	private static int effectivePort(URI uri) {
		if (uri.getPort() != -1) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}
