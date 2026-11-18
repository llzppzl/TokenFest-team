package com.tokenfest.gateway.proxy;

import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.provider.ProviderResponse;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class StreamingAndFallbackTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension primary = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@RegisterExtension
	static WireMockExtension backup = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageDailyRepository usageDaily;

	@Autowired
	private UsageRecorder recorder;

	private static final String CHAT = """
			{"model": "gpt-4o", "messages": [{"role": "user", "content": "Hi"}]}""";

	private static final String STREAM_CHAT = """
			{"model": "gpt-4o", "stream": true, "messages": [{"role": "user", "content": "Hi"}]}""";

	private static final String CHAT_RESPONSE = """
			{"id": "1", "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hello!"}}],
			 "usage": {"prompt_tokens": 1000, "completion_tokens": 500, "total_tokens": 1500}}""";

	private static final String OPENAI_SSE = """
			data: {"id":"1","choices":[{"index":0,"delta":{"content":"Hel"}}]}

			data: {"id":"1","choices":[{"index":0,"delta":{"content":"lo"}}]}

			data: {"id":"1","choices":[],"usage":{"prompt_tokens":1000,"completion_tokens":500,"total_tokens":1500}}

			data: [DONE]

			""";

	private String chat(String key, String body, int expectedStatus) {
		return client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.exchange()
			.expectStatus()
			.isEqualTo(expectedStatus)
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

	private RequestLog onlyLog() {
		recorder.awaitIdle();
		List<RequestLog> logs = requestLogs.findAll();
		assertThat(logs).hasSize(1);
		return logs.get(0);
	}

	@Test
	void nonStreamingUsageAndCostAreRecorded() {
		ProviderResponse provider = createProvider("openai", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10,
				"gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(CHAT_RESPONSE)));

		chat(key, CHAT, 200);

		RequestLog log = onlyLog();
		assertThat(log.getProviderId()).isEqualTo(provider.id());
		assertThat(log.getInputTokens()).isEqualTo(1000);
		assertThat(log.getOutputTokens()).isEqualTo(500);
		// prices from createProvider: $2/M input, $8/M output
		assertThat(log.getCost()).isEqualByComparingTo("0.006");
		assertThat(log.isStreamed()).isFalse();
		assertThat(log.getRequestBody()).contains("\"Hi\"");
		assertThat(log.getResponseBody()).contains("Hello!");

		UsageDaily daily = usageDaily.findAll().get(0);
		assertThat(daily.getRequestCount()).isEqualTo(1);
		assertThat(daily.getCost()).isEqualByComparingTo("0.006");
	}

	@Test
	void openAiStreamIsPassedThroughAndUsageRecorded() {
		createProvider("openai", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse()
			.withHeader("Content-Type", "text/event-stream")
			.withBody(OPENAI_SSE)
			.withChunkedDribbleDelay(10, 200)));

		String body = chat(key, STREAM_CHAT, 200);

		assertThat(body).contains("Hel", "lo", "[DONE]").doesNotContain("prompt_tokens");
		primary.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
			.withRequestBody(matchingJsonPath("$.stream_options.include_usage", com.github.tomakehurst.wiremock.client.WireMock.equalTo("true"))));
		RequestLog log = onlyLog();
		assertThat(log.isStreamed()).isTrue();
		assertThat(log.getInputTokens()).isEqualTo(1000);
		assertThat(log.getCost()).isEqualByComparingTo("0.006");
	}

	@Test
	void anthropicStreamUsageIncludesCache() {
		createProvider("anthropic", ProviderType.ANTHROPIC, primary.baseUrl(), 10, "sonnet-4-5");
		String key = createKey("devA");
		primary.stubFor(post("/v1/messages").willReturn(aResponse()
			.withHeader("Content-Type", "text/event-stream")
			.withBody("""
					event: message_start
					data: {"type":"message_start","message":{"usage":{"input_tokens":1000,"cache_read_input_tokens":10000,"output_tokens":1}}}

					event: message_delta
					data: {"type":"message_delta","usage":{"output_tokens":500}}

					event: message_stop
					data: {"type":"message_stop"}

					""")));

		String body = client.post()
			.uri("/v1/messages")
			.header("x-api-key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\": \"sonnet-4-5\", \"stream\": true, \"max_tokens\": 10, \"messages\": []}")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		assertThat(body).contains("message_stop");
		RequestLog log = onlyLog();
		assertThat(log.getInputTokens()).isEqualTo(11000);
		assertThat(log.getOutputTokens()).isEqualTo(500);
		// 1000 * $2/M + 10000 * $0.2/M + 500 * $8/M = 0.002 + 0.002 + 0.004
		assertThat(log.getCost()).isEqualByComparingTo("0.008");
	}

	@Test
	void fallsBackWhenFirstProviderFails() {
		createProvider("primary", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		ProviderResponse second = createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20,
				"gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(503).withBody("overloaded")));
		backup.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(CHAT_RESPONSE)));

		assertThat(chat(key, CHAT, 200)).contains("Hello!");

		RequestLog log = onlyLog();
		assertThat(log.getProviderId()).isEqualTo(second.id());
		assertThat(log.getAttempts()).isEqualTo(2);
		assertThat(log.getErrorMessage()).contains("primary returned 503");
	}

	@Test
	void fallsBackWhenFirstProviderIsUnreachable() {
		createProvider("dead", ProviderType.OPENAI, "http://127.0.0.1:1/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		backup.stubFor(post("/v1/chat/completions").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody(CHAT_RESPONSE)));

		assertThat(chat(key, CHAT, 200)).contains("Hello!");
		assertThat(onlyLog().getErrorMessage()).contains("Could not reach provider 'dead'");
	}

	@Test
	void streamingRequestFallsBackBeforeStreamStarts() {
		createProvider("primary", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(429)));
		backup.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(OPENAI_SSE)));

		assertThat(chat(key, STREAM_CHAT, 200)).contains("Hel", "[DONE]");
	}

	@Test
	void clientErrorsDoNotFallBack() {
		createProvider("primary", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(400)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":{\"message\":\"bad\"}}")));

		chat(key, CHAT, 400);
		backup.verify(0, postRequestedFor(urlEqualTo("/v1/chat/completions")));
		RequestLog log = onlyLog();
		assertThat(log.getStatusCode()).isEqualTo(400);
		assertThat(log.getCost()).isZero();
		recorder.awaitIdle();
		assertThat(usageDaily.count()).isZero();
	}

	@Test
	void lastProviderErrorIsReturnedWhenAllFail() {
		createProvider("primary", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(500)));
		backup.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(503)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":{\"message\":\"backup down\"}}")));

		assertThat(chat(key, CHAT, 503)).contains("backup down");
		assertThat(onlyLog().getAttempts()).isEqualTo(2);
	}

	@Test
	void allProvidersUnreachableGives502() {
		createProvider("dead", ProviderType.OPENAI, "http://127.0.0.1:1/v1", 10, "gpt-4o");
		String key = createKey("devA");

		assertThat(chat(key, CHAT, 502)).contains("upstream_error");
		RequestLog log = onlyLog();
		assertThat(log.getStatusCode()).isEqualTo(502);
	}

	@Test
	void gatewayErrorsAreLoggedForKnownKeys() {
		String key = createKey("devA");
		chat(key, "{\"model\": \"nope\"}", 404);
		RequestLog log = onlyLog();
		assertThat(log.getStatusCode()).isEqualTo(404);
		assertThat(log.getErrorMessage()).contains("nope");
	}

}
