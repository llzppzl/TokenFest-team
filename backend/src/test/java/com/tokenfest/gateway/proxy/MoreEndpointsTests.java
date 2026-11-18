package com.tokenfest.gateway.proxy;

import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/** Responses API, embeddings, and Anthropic token counting. */
class MoreEndpointsTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension upstream = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageRecorder recorder;

	private String send(String key, String path, String body, int status) {
		return client.post()
			.uri(path)
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.exchange()
			.expectStatus()
			.isEqualTo(status)
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
	void responsesApiIsForwardedAndPriced() {
		createProvider("openai", ProviderType.OPENAI, upstream.baseUrl() + "/v1", 10, "gpt-5");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/responses").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"id\":\"r1\",\"output\":[],\"usage\":{\"input_tokens\":1000,\"output_tokens\":500}}")));

		assertThat(send(key, "/v1/responses", "{\"model\":\"gpt-5\",\"input\":\"hi\"}", 200)).contains("r1");
		RequestLog log = onlyLog();
		assertThat(log.getInputTokens()).isEqualTo(1000);
		assertThat(log.getCost()).isEqualByComparingTo("0.006"); // 1000*$2/M + 500*$8/M
	}

	@Test
	void responsesApiStreamUsesCompletedEvent() {
		createProvider("openai", ProviderType.OPENAI, upstream.baseUrl() + "/v1", 10, "gpt-5");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/responses").willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
			.withBody("""
					event: response.output_text.delta
					data: {"type":"response.output_text.delta","delta":"Hi"}

					event: response.completed
					data: {"type":"response.completed","response":{"usage":{"input_tokens":2000,"output_tokens":0}}}

					""")));

		assertThat(send(key, "/v1/responses", "{\"model\":\"gpt-5\",\"stream\":true,\"input\":\"hi\"}", 200))
			.contains("response.completed");
		// The request body must not be changed for the Responses API
		upstream.verify(postRequestedFor(urlEqualTo("/v1/responses"))
			.withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.notContaining("stream_options")));
		assertThat(onlyLog().getCost()).isEqualByComparingTo("0.004");
	}

	@Test
	void embeddingsArePriced() {
		createProvider("openai", ProviderType.OPENAI, upstream.baseUrl() + "/v1", 10, "text-embedding-3-small");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/embeddings").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"data\":[{\"embedding\":[0.1]}],\"usage\":{\"prompt_tokens\":500,\"total_tokens\":500}}")));

		send(key, "/v1/embeddings", "{\"model\":\"text-embedding-3-small\",\"input\":\"hi\"}", 200);
		assertThat(onlyLog().getCost()).isEqualByComparingTo("0.001");
	}

	@Test
	void countTokensIsForwardedButNotLoggedOrRateLimited() {
		createProvider("anthropic", ProviderType.ANTHROPIC, upstream.baseUrl(), 10, "sonnet-4-5");
		String key = createKey(new KeyRequest("devA", null, 1, List.of(), true));
		upstream.stubFor(post("/v1/messages/count_tokens").willReturn(
				aResponse().withHeader("Content-Type", "application/json").withBody("{\"input_tokens\":42}")));

		for (int i = 0; i < 3; i++) {
			String body = client.post()
				.uri("/v1/messages/count_tokens")
				.header("x-api-key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"model\":\"sonnet-4-5\",\"messages\":[]}")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody(String.class)
				.returnResult()
				.getResponseBody();
			assertThat(body).contains("42");
		}
		recorder.awaitIdle();
		assertThat(requestLogs.count()).isZero();
	}

}
