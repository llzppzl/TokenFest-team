package com.tokenfest.gateway.proxy;

import com.tokenfest.gateway.domain.ProviderType;
import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * OpenAI- and Anthropic-compatible endpoints. Clients only change their base URL and use
 * their TokenFest key.
 */
@RestController
public class ProxyController {

	private final ProxyService proxy;

	public ProxyController(ProxyService proxy) {
		this.proxy = proxy;
	}

	@PostMapping("/v1/chat/completions")
	public Mono<Void> chatCompletions(ServerWebExchange exchange) {
		return proxy.forward(Endpoint.CHAT_COMPLETIONS, exchange);
	}

	@PostMapping("/v1/responses")
	public Mono<Void> responses(ServerWebExchange exchange) {
		return proxy.forward(Endpoint.RESPONSES, exchange);
	}

	@PostMapping("/v1/embeddings")
	public Mono<Void> embeddings(ServerWebExchange exchange) {
		return proxy.forward(Endpoint.EMBEDDINGS, exchange);
	}

	@PostMapping("/v1/messages")
	public Mono<Void> messages(ServerWebExchange exchange) {
		return proxy.forward(Endpoint.MESSAGES, exchange);
	}

	@PostMapping("/v1/messages/count_tokens")
	public Mono<Void> countTokens(ServerWebExchange exchange) {
		return proxy.forward(Endpoint.COUNT_TOKENS, exchange);
	}

	/** Anthropic SDKs send an {@code anthropic-version} header; OpenAI SDKs don't. */
	@GetMapping("/v1/models")
	public Mono<Void> models(ServerWebExchange exchange) {
		boolean anthropic = exchange.getRequest().getHeaders().containsHeader("anthropic-version");
		return proxy.listModels(anthropic ? ProviderType.ANTHROPIC : ProviderType.OPENAI, exchange);
	}

}
