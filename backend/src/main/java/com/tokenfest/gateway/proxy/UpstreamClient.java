package com.tokenfest.gateway.proxy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.tokenfest.gateway.domain.ProviderType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

/**
 * Sends a request to one provider with the provider's own API key.
 */
@Component
public class UpstreamClient {

	static final String DEFAULT_ANTHROPIC_VERSION = "2023-06-01";

	private final WebClient webClient;

	private final JsonMapper json;

	public UpstreamClient(WebClient upstreamWebClient, JsonMapper json) {
		this.webClient = upstreamWebClient;
		this.json = json;
	}

	/**
	 * A response whose status and headers have arrived. The body has not been read yet
	 * and must be consumed or released.
	 */
	public record UpstreamCall(Route route, int status, HttpHeaders headers, Flux<DataBuffer> body) {

		MediaType contentType() {
			return headers.getContentType();
		}

		boolean isEventStream() {
			MediaType type = contentType();
			return type != null && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(type);
		}

	}

	/** The provider could not be reached (connection error or timeout). */
	public static class UpstreamUnavailableException extends RuntimeException {

		UpstreamUnavailableException(Route route, String reason, Throwable cause) {
			super("Could not reach provider '" + route.providerName() + "'" + (reason != null ? " (" + reason + ")" : ""),
					cause);
		}

	}

	/** Result of a provider connection test from the dashboard. */
	public record ConnectionTest(boolean ok, int status, long latencyMs, String message, List<String> models) {
	}

	/**
	 * Sends the request and returns as soon as the status and headers arrive. Error
	 * statuses are returned like any other response so the caller can decide what to do.
	 * @param headersTimeout how long to wait for the status and headers before giving up
	 * on this provider
	 */
	public Mono<UpstreamCall> open(Route route, ProviderType format, String path, byte[] body,
			HttpHeaders clientHeaders, Duration headersTimeout) {
		return webClient.post()
			.uri(route.baseUrl() + path)
			.headers(h -> applyHeaders(h, route.apiKey(), format, clientHeaders))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.onStatus(status -> true, response -> Mono.empty())
			.toEntityFlux(DataBuffer.class)
			.timeout(headersTimeout)
			.map(entity -> new UpstreamCall(route, entity.getStatusCode().value(), entity.getHeaders(),
					entity.getBody() != null ? entity.getBody() : Flux.empty()))
			.onErrorMap(TimeoutException.class, e -> new UpstreamUnavailableException(route,
					"no answer within " + headersTimeout.toSeconds() + "s", e))
			.onErrorMap(WebClientRequestException.class, e -> isPoolExhausted(e)
					? new ProxyException(HttpStatus.SERVICE_UNAVAILABLE, "gateway_busy",
							"The gateway is handling too many requests right now. Try again shortly.", 2L)
					: new UpstreamUnavailableException(route, null, e));
	}

	/**
	 * Our own connection pool is full. That says nothing about the provider, so it must not
	 * trigger fallback or cooldown.
	 */
	static boolean isPoolExhausted(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
			if (t.getClass().getSimpleName().startsWith("PoolAcquire")) {
				return true;
			}
		}
		return false;
	}

	/** Lists the provider's models to check the URL and key. Never throws. */
	public Mono<ConnectionTest> test(String baseUrl, String apiKey, ProviderType format) {
		long start = System.nanoTime();
		String path = format == ProviderType.ANTHROPIC ? "/v1/models" : "/models";
		return webClient.get()
			.uri(baseUrl + path)
			.headers(h -> applyHeaders(h, apiKey, format, new HttpHeaders()))
			.retrieve()
			.onStatus(status -> true, response -> Mono.empty())
			.toEntity(byte[].class)
			.timeout(Duration.ofSeconds(15))
			.map(entity -> {
				long ms = (System.nanoTime() - start) / 1_000_000;
				int status = entity.getStatusCode().value();
				if (status >= 200 && status < 300) {
					return new ConnectionTest(true, status, ms, "Connected", modelIds(entity.getBody()));
				}
				String hint = status == 401 || status == 403 ? "the API key was rejected" : "unexpected response";
				return new ConnectionTest(false, status, ms, "Provider answered " + status + ": " + hint, List.of());
			})
			.onErrorResume(e -> Mono.just(new ConnectionTest(false, 0, (System.nanoTime() - start) / 1_000_000,
					e instanceof TimeoutException ? "No answer within 15 seconds" : "Could not connect: " + rootMessage(e),
					List.of())));
	}

	private List<String> modelIds(byte[] body) {
		List<String> ids = new ArrayList<>();
		try {
			for (JsonNode model : json.readTree(body).path("data")) {
				if (model.path("id").isString() && ids.size() < 50) {
					ids.add(model.path("id").stringValue());
				}
			}
		}
		catch (RuntimeException e) {
			// not a model list; the connection still worked
		}
		return ids;
	}

	private static String rootMessage(Throwable e) {
		Throwable root = e;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
	}

	static void applyHeaders(HttpHeaders headers, String apiKey, ProviderType format, HttpHeaders clientHeaders) {
		if (format == ProviderType.ANTHROPIC) {
			headers.set("x-api-key", apiKey);
			String version = clientHeaders.getFirst("anthropic-version");
			headers.set("anthropic-version", version != null ? version : DEFAULT_ANTHROPIC_VERSION);
			String beta = clientHeaders.getFirst("anthropic-beta");
			if (beta != null) {
				headers.set("anthropic-beta", beta);
			}
		}
		else {
			headers.setBearerAuth(apiKey);
		}
	}

}
