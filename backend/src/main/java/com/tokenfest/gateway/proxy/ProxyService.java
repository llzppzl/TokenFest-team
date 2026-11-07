package com.tokenfest.gateway.proxy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.tokenfest.gateway.config.Blocking;
import com.tokenfest.gateway.config.TokenFestProperties;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.ApiKeyService;
import com.tokenfest.gateway.keys.KeyInfo;
import com.tokenfest.gateway.limits.ConcurrencyLimiter;
import com.tokenfest.gateway.limits.LimitChecker;
import com.tokenfest.gateway.proxy.UpstreamClient.UpstreamCall;
import com.tokenfest.gateway.proxy.UpstreamClient.UpstreamUnavailableException;
import com.tokenfest.gateway.usage.CostCalculator;
import com.tokenfest.gateway.usage.RequestRecord;
import com.tokenfest.gateway.usage.Usage;
import com.tokenfest.gateway.usage.UsageRecorder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

/**
 * Handles one proxied request: check the key, pick providers, forward with fallback,
 * stream the response back, and record usage.
 */
@Service
public class ProxyService {

	static final int MAX_REQUEST_BYTES = 32 * 1024 * 1024;

	private final ApiKeyService apiKeys;

	private final LimitChecker limits;

	private final ConcurrencyLimiter concurrency;

	private final ProviderRouter router;

	private final ProviderHealth health;

	private final UpstreamClient upstream;

	private final ProxyErrors errors;

	private final UsageRecorder recorder;

	private final JsonMapper json;

	private final Clock clock;

	private final int maxBodyChars;

	private final boolean logBodies;

	private final Duration readTimeout;

	private final Duration streamHeadersTimeout;

	/** Upstream response headers passed on to the client (SDKs use them to back off). */
	static final Set<String> FORWARDED_RESPONSE_HEADERS = Set.of("retry-after", "retry-after-ms", "x-should-retry",
			"request-id", "x-request-id");

	public ProxyService(ApiKeyService apiKeys, LimitChecker limits, ConcurrencyLimiter concurrency,
			ProviderRouter router, ProviderHealth health, UpstreamClient upstream, ProxyErrors errors,
			UsageRecorder recorder, JsonMapper json, Clock clock, TokenFestProperties properties,
			@Value("${tokenfest.log-bodies:true}") boolean logBodies) {
		this.apiKeys = apiKeys;
		this.limits = limits;
		this.concurrency = concurrency;
		this.router = router;
		this.health = health;
		this.upstream = upstream;
		this.errors = errors;
		this.recorder = recorder;
		this.json = json;
		this.clock = clock;
		this.maxBodyChars = properties.maxBodyChars();
		this.logBodies = logBodies;
		this.readTimeout = properties.upstream().readTimeout();
		this.streamHeadersTimeout = properties.upstream().streamHeadersTimeout();
	}

	/** Fields of a chat request that the gateway needs. */
	record ChatRequest(String model, boolean stream, boolean clientWantsUsage) {
	}

	/** What we know about the request so far; recorded when it finishes. */
	private static final class Exchange {

		final Endpoint endpoint;

		final ProviderType format;

		final Instant startedAt;

		final long startNanos = System.nanoTime();

		KeyInfo key;

		byte[] requestBody;

		String model;

		boolean stream;

		Route route;

		int status;

		int attempts;

		/** Counted in the key's concurrent requests; released when the request ends. */
		boolean holdsSlot;

		final AtomicBoolean recorded = new AtomicBoolean();

		// Written from the upstream thread and read when the client cancels
		final List<String> problems = Collections.synchronizedList(new ArrayList<>());

		Exchange(Endpoint endpoint, Instant startedAt) {
			this.endpoint = endpoint;
			this.format = endpoint.format();
			this.startedAt = startedAt;
		}

	}

	public Mono<Void> forward(Endpoint endpoint, ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		HttpHeaders clientHeaders = exchange.getRequest().getHeaders();
		ProviderType format = endpoint.format();
		Exchange ex = new Exchange(endpoint, Instant.now(clock));
		return authenticate(exchange).flatMap(key -> {
			ex.key = key;
			// Limits come before reading the body, so a blocked key can't make us buffer large requests
			return Blocking.call(() -> {
				if (endpoint.billable()) {
					limits.check(key);
				}
				return key;
			});
		}).flatMap(key -> {
			if (endpoint.billable()) {
				concurrency.acquire(key);
				ex.holdsSlot = true;
			}
			return readBody(exchange);
		}).flatMap(body -> {
			ex.requestBody = body;
			ChatRequest request = parse(body);
			ex.model = request.model();
			ex.stream = request.stream();
			byte[] upstreamBody = prepareBody(endpoint, request, body);
			return Blocking.call(() -> health.order(router.routes(ex.key, format, request.model()))).flatMap(routes -> {
				if (routes.isEmpty()) {
					return Mono.error(ProxyException.modelNotFound(request.model()));
				}
				return tryRoutes(routes, 0, ex, upstreamBody, clientHeaders);
			}).flatMap(call -> writeResponse(response, call, ex, request));
		}).onErrorResume(e -> {
			ProxyException error = e instanceof ProxyException pe ? pe
					: ProxyException.upstream("Unexpected gateway error: " + e.getClass().getSimpleName());
			ex.status = error.getStatus().value();
			// upstream_error already lists every provider problem
			if (!"upstream_error".equals(error.getCode()) || ex.problems.isEmpty()) {
				ex.problems.add(error.getMessage());
			}
			if (response.isCommitted()) {
				// Failed mid-stream: the stream's own doFinally records it with its usage
				return Mono.empty();
			}
			record(ex, Usage.ZERO, null);
			return errors.write(response, format, error);
		}).doFinally(signal -> {
			if (ex.holdsSlot) {
				concurrency.release(ex.key);
			}
			if (signal == SignalType.CANCEL && !ex.recorded.get()) {
				// The client gave up before the answer came. The provider may still bill the
				// prompt, so record an estimate of it.
				ex.status = 499;
				ex.problems.add("Client disconnected before the response");
				record(ex, Usage.ZERO.withEstimate(ex.requestBody != null ? ex.requestBody.length : 0, 0), null);
			}
		});
	}

	/**
	 * Tries providers in order. Moves to the next one on 408, 429, 5xx, timeout or
	 * connection error. The last provider's response is returned whatever it is.
	 */
	private Mono<UpstreamCall> tryRoutes(List<Route> routes, int index, Exchange ex, byte[] body,
			HttpHeaders clientHeaders) {
		Route route = routes.get(index);
		boolean last = index == routes.size() - 1;
		ex.attempts++;
		ex.route = route;
		// Streams send headers right away; a normal response only after the whole answer
		Duration headersTimeout = ex.stream ? streamHeadersTimeout : readTimeout;
		return upstream.open(route, ex.format, ex.endpoint.upstreamPath(), body, clientHeaders, headersTimeout)
			.flatMap(call -> {
				boolean keyRejected = isKeyRejected(call.status());
				boolean failed = keyRejected || shouldTryNext(call.status());
				if (failed) {
					health.recordFailure(route.providerId());
				}
				else {
					health.recordSuccess(route.providerId());
				}
				if (!failed || (last && !keyRejected)) {
					return Mono.just(call);
				}
				return drain(call.body()).flatMap(errorBody -> {
					ex.problems.add(route.providerName()
							+ (keyRejected ? " rejected the gateway's API key (" + call.status() + ")"
									: " returned " + call.status()));
					if (last) {
						// Not the client's fault: their TokenFest key is fine
						return Mono.error(ProxyException.upstream(String.join("; ", ex.problems)));
					}
					return tryRoutes(routes, index + 1, ex, body, clientHeaders);
				});
			})
			.onErrorResume(UpstreamUnavailableException.class, e -> {
				health.recordFailure(route.providerId());
				ex.problems.add(e.getMessage());
				if (last) {
					return Mono.error(ProxyException.upstream(String.join("; ", ex.problems)));
				}
				return tryRoutes(routes, index + 1, ex, body, clientHeaders);
			});
	}

	static boolean shouldTryNext(int status) {
		return status == 408 || status == 429 || status >= 500;
	}

	/**
	 * Clients' keys are never sent upstream, so 401/403 from a provider always means the
	 * gateway's own provider key is wrong or revoked.
	 */
	static boolean isKeyRejected(int status) {
		return status == 401 || status == 403;
	}

	private Mono<Void> writeResponse(ServerHttpResponse response, UpstreamCall call, Exchange ex,
			ChatRequest request) {
		ex.status = call.status();
		response.setStatusCode(HttpStatusCode.valueOf(call.status()));
		response.getHeaders()
			.setContentType(call.contentType() != null ? call.contentType() : MediaType.APPLICATION_JSON);
		call.headers().forEach((name, values) -> {
			if (FORWARDED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
				response.getHeaders().put(name, values);
			}
		});
		if (call.isEventStream()) {
			response.getHeaders().setCacheControl("no-cache");
			boolean dropUsageChunks = ex.endpoint == Endpoint.CHAT_COMPLETIONS && !request.clientWantsUsage();
			SseProcessor sse = new SseProcessor(ex.endpoint, dropUsageChunks, json, maxBodyChars);
			Flux<DataBuffer> events = sse.process(call.body(), response.bufferFactory())
				.doOnError(e -> ex.problems.add("Stream failed: " + e.getMessage()))
				.doFinally(signal -> {
					if (signal == SignalType.CANCEL) {
						ex.problems.add("Client disconnected");
					}
					Usage usage = sse.usage();
					if (isSuccess(call.status()) && !sse.finalUsageSeen()) {
						// The stream ended before the provider sent its numbers; don't let it count as free
						usage = usage.withEstimate(ex.requestBody != null ? ex.requestBody.length : 0, sse.outputChars());
						ex.problems.add("Usage estimated: the stream ended before the provider reported it");
					}
					record(ex, usage, sse.capturedText());
				});
			return response.writeAndFlushWith(events.map(Mono::just));
		}
		return DataBufferUtils.join(call.body(), MAX_REQUEST_BYTES).map(ProxyService::toBytes).defaultIfEmpty(new byte[0])
			.flatMap(bytes -> {
				Usage usage = isSuccess(call.status()) ? parseUsage(ex.endpoint, bytes) : Usage.ZERO;
				record(ex, usage, new String(bytes, StandardCharsets.UTF_8));
				return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
			});
	}

	/** Records the request once; later calls do nothing. */
	private void record(Exchange ex, Usage usage, String responseBody) {
		if (ex.key == null || !ex.endpoint.billable() || !ex.recorded.compareAndSet(false, true)) {
			return; // unknown key, a free endpoint like token counting, or already recorded
		}
		// 499 = client left early; the prompt estimate is still charged
		BigDecimal cost = ex.route != null && (isSuccess(ex.status) || ex.status == 499)
				? CostCalculator.cost(usage, ex.route.inputPrice(), ex.route.outputPrice()) : BigDecimal.ZERO;
		int latencyMs = (int) ((System.nanoTime() - ex.startNanos) / 1_000_000);
		String requestBody = logBodies && ex.requestBody != null ? new String(ex.requestBody, StandardCharsets.UTF_8)
				: null;
		if (!logBodies) {
			responseBody = null;
		}
		recorder.record(new RequestRecord(ex.key.id(), ex.route != null ? ex.route.providerId() : null, ex.format,
				ex.model, ex.status, ex.stream, usage, cost, latencyMs, Math.max(ex.attempts, 1),
				ex.problems.isEmpty() ? null : truncate(String.join("; ", ex.problems), 1000),
				truncate(requestBody, maxBodyChars), truncate(responseBody, maxBodyChars), ex.startedAt));
	}

	/** {@code GET /v1/models} in the OpenAI or Anthropic list format. */
	public Mono<Void> listModels(ProviderType format, ServerWebExchange exchange) {
		ServerHttpResponse response = exchange.getResponse();
		return authenticate(exchange).flatMap(key -> Blocking.call(() -> router.models(key, format)))
			.flatMap(models -> {
				response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
				byte[] body = json.writeValueAsBytes(modelList(format, models));
				return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
			})
			.onErrorResume(ProxyException.class, e -> errors.write(response, format, e));
	}

	private Mono<KeyInfo> authenticate(ServerWebExchange exchange) {
		String rawKey = ApiKeyService.extractKey(exchange.getRequest().getHeaders());
		return Blocking.call(() -> apiKeys.authenticate(rawKey).orElseThrow(ProxyException::invalidKey));
	}

	private Mono<byte[]> readBody(ServerWebExchange exchange) {
		return DataBufferUtils.join(exchange.getRequest().getBody(), MAX_REQUEST_BYTES)
			.map(ProxyService::toBytes)
			.defaultIfEmpty(new byte[0])
			.onErrorMap(DataBufferLimitException.class, e -> new ProxyException(HttpStatus.PAYLOAD_TOO_LARGE,
					"request_too_large", "Request body is larger than 32 MB"));
	}

	ChatRequest parse(byte[] body) {
		JsonNode root;
		try {
			root = json.readTree(body);
		}
		catch (JacksonException e) {
			throw ProxyException.badRequest("Request body is not valid JSON");
		}
		JsonNode model = root.path("model");
		if (!model.isString() || model.stringValue().isBlank()) {
			throw ProxyException.badRequest("'model' is required");
		}
		boolean wantsUsage = root.path("stream_options").path("include_usage").asBoolean(false);
		return new ChatRequest(model.stringValue(), root.path("stream").asBoolean(false), wantsUsage);
	}

	/**
	 * OpenAI only reports usage in a stream when asked, so ask for it. The extra chunk is
	 * removed again before it reaches a client that did not ask.
	 */
	private byte[] prepareBody(Endpoint endpoint, ChatRequest request, byte[] body) {
		if (endpoint != Endpoint.CHAT_COMPLETIONS || !request.stream() || request.clientWantsUsage()) {
			return body;
		}
		ObjectNode root = (ObjectNode) json.readTree(body);
		JsonNode options = root.path("stream_options");
		ObjectNode streamOptions = options.isObject() ? (ObjectNode) options : root.putObject("stream_options");
		streamOptions.put("include_usage", true);
		return json.writeValueAsBytes(root);
	}

	private Usage parseUsage(Endpoint endpoint, byte[] body) {
		try {
			JsonNode usage = json.readTree(body).path("usage");
			if (!usage.isObject()) {
				return Usage.ZERO;
			}
			return switch (endpoint) {
				case MESSAGES -> Usage.fromAnthropic(usage);
				case RESPONSES -> Usage.fromResponses(usage);
				case CHAT_COMPLETIONS, EMBEDDINGS -> Usage.fromOpenAi(usage);
				case COUNT_TOKENS -> Usage.ZERO;
			};
		}
		catch (JacksonException e) {
			return Usage.ZERO;
		}
	}

	private static Mono<String> drain(Flux<DataBuffer> body) {
		return DataBufferUtils.join(body, 64 * 1024)
			.map(buffer -> new String(toBytes(buffer), StandardCharsets.UTF_8))
			.onErrorReturn("")
			.defaultIfEmpty("");
	}

	private static byte[] toBytes(DataBuffer buffer) {
		byte[] bytes = new byte[buffer.readableByteCount()];
		buffer.read(bytes);
		DataBufferUtils.release(buffer);
		return bytes;
	}

	private static boolean isSuccess(int status) {
		return status >= 200 && status < 300;
	}

	private static final String TRUNCATED = "...[truncated]";

	/** Cuts text to at most {@code max} characters, marker included. */
	static String truncate(String text, int max) {
		if (text == null || text.length() <= max) {
			return text;
		}
		return text.substring(0, max - TRUNCATED.length()) + TRUNCATED;
	}

	private static Map<String, Object> modelList(ProviderType format, Map<String, String> models) {
		List<Map<String, Object>> data = new ArrayList<>();
		if (format == ProviderType.ANTHROPIC) {
			models.keySet()
				.forEach(id -> data.add(Map.of("type", "model", "id", id, "display_name", id, "created_at",
						"1970-01-01T00:00:00Z")));
			List<String> ids = List.copyOf(models.keySet());
			Map<String, Object> result = new HashMap<>();
			result.put("data", data);
			result.put("has_more", false);
			result.put("first_id", ids.isEmpty() ? null : ids.getFirst());
			result.put("last_id", ids.isEmpty() ? null : ids.getLast());
			return result;
		}
		models.forEach((id, owner) -> data.add(Map.of("id", id, "object", "model", "created", 0, "owned_by", owner)));
		return Map.of("object", "list", "data", data);
	}

}
