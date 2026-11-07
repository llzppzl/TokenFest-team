package com.tokenfest.gateway.proxy;

import java.util.Map;

import com.tokenfest.gateway.domain.ProviderType;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Writes gateway errors in the same shape that OpenAI or Anthropic would use, so SDKs
 * show a readable message.
 */
@Component
public class ProxyErrors {

	private final JsonMapper json;

	public ProxyErrors(JsonMapper json) {
		this.json = json;
	}

	public Mono<Void> write(ServerHttpResponse response, ProviderType format, ProxyException e) {
		response.setStatusCode(e.getStatus());
		response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS && e.getRetryAfterSeconds() != null) {
			response.getHeaders().set("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
		}
		byte[] body = json.writeValueAsBytes(body(format, e));
		return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
	}

	static Map<String, Object> body(ProviderType format, ProxyException e) {
		if (format == ProviderType.ANTHROPIC) {
			return Map.of("type", "error", "error",
					Map.of("type", anthropicType(e.getStatus()), "message", e.getMessage()));
		}
		return Map.of("error", Map.of("message", e.getMessage(), "type", "tokenfest_error", "code", e.getCode()));
	}

	private static String anthropicType(HttpStatus status) {
		return switch (status) {
			case UNAUTHORIZED -> "authentication_error";
			case FORBIDDEN -> "permission_error";
			case NOT_FOUND -> "not_found_error";
			case TOO_MANY_REQUESTS -> "rate_limit_error";
			case BAD_REQUEST, PAYLOAD_TOO_LARGE -> "invalid_request_error";
			default -> "api_error";
		};
	}

}
