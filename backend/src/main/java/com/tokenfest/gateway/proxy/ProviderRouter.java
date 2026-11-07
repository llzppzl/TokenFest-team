package com.tokenfest.gateway.proxy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.KeyInfo;
import com.tokenfest.gateway.provider.ProviderCatalog;
import com.tokenfest.gateway.provider.ProviderCatalog.Price;
import com.tokenfest.gateway.provider.ProviderCatalog.ProviderSnapshot;

import org.springframework.stereotype.Component;

/**
 * Picks the providers that can serve a request: enabled, same API format, serving the
 * model, and allowed for the key. Ordered by priority (lower first).
 */
@Component
public class ProviderRouter {

	private final ProviderCatalog catalog;

	public ProviderRouter(ProviderCatalog catalog) {
		this.catalog = catalog;
	}

	/** Blocking on a cache miss: call from {@code Blocking.call}. */
	public List<Route> routes(KeyInfo key, ProviderType format, String model) {
		List<Route> routes = new ArrayList<>();
		for (ProviderSnapshot provider : usableProviders(key, format)) {
			Price price = provider.models().get(model);
			if (price != null) {
				routes.add(new Route(provider.id(), provider.name(), provider.baseUrl(), provider.apiKey(),
						price.input(), price.output()));
			}
		}
		return routes;
	}

	/** Model name to the name of the first provider serving it. */
	public Map<String, String> models(KeyInfo key, ProviderType format) {
		Map<String, String> models = new LinkedHashMap<>();
		for (ProviderSnapshot provider : usableProviders(key, format)) {
			provider.models().keySet().stream().sorted().forEach(m -> models.putIfAbsent(m, provider.name()));
		}
		return models;
	}

	private List<ProviderSnapshot> usableProviders(KeyInfo key, ProviderType format) {
		return catalog.enabledProviders()
			.stream()
			.filter(p -> p.type() == format)
			.filter(p -> key.allowedProviderIds().isEmpty() || key.allowedProviderIds().contains(p.id()))
			.toList();
	}

}
