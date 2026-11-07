package com.tokenfest.gateway.proxy;

import java.math.BigDecimal;

/**
 * One provider that can serve a request, with the decrypted API key and the model price.
 */
public record Route(Long providerId, String providerName, String baseUrl, String apiKey, BigDecimal inputPrice,
		BigDecimal outputPrice) {

	@Override
	public String toString() {
		// Never print the API key
		return "Route[" + providerName + "]";
	}

}
