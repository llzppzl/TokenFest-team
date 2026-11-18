package com.tokenfest.gateway.proxy;

import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.provider.ProviderResponse;
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

/** Timeouts, provider cooldown and forwarded headers. */
class ResilienceTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension primary = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@RegisterExtension
	static WireMockExtension backup = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageRecorder recorder;

	private static final String OK = "{\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1}}";

	private String chat(String key, boolean stream, int expected) {
		return client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\":\"gpt-4o\",\"stream\":" + stream + "}")
			.exchange()
			.expectStatus()
			.isEqualTo(expected)
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
	}

	@Test
	void slowStreamingProviderIsSkippedAfterTheHeadersTimeout() {
		createProvider("slow", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		// test profile: stream-headers-timeout = 1s
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withFixedDelay(4000)
			.withHeader("Content-Type", "text/event-stream")
			.withBody("data: [DONE]\n\n")));
		backup.stubFor(post("/v1/chat/completions").willReturn(
				aResponse().withHeader("Content-Type", "text/event-stream").withBody("data: {\"choices\":[{\"delta\":{\"content\":\"fast\"}}]}\n\ndata: [DONE]\n\n")));

		long start = System.currentTimeMillis();
		assertThat(chat(key, true, 200)).contains("fast");
		assertThat(System.currentTimeMillis() - start).isLessThan(3500);
		recorder.awaitIdle();
		assertThat(requestLogs.findAll().get(0).getErrorMessage()).contains("no answer within 1s");
	}

	@Test
	void failingProviderCoolsDownAndIsTriedLast() {
		ProviderResponse bad = createProvider("flaky", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(503)));
		backup.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(OK)));

		for (int i = 0; i < 5; i++) {
			chat(key, false, 200);
		}
		// 3 failures put it in cooldown; the next requests go straight to the backup
		primary.verify(3, postRequestedFor(urlEqualTo("/v1/chat/completions")));
		backup.verify(5, postRequestedFor(urlEqualTo("/v1/chat/completions")));

		admin.get()
			.uri("/api/admin/providers")
			.exchange()
			.expectBody()
			.jsonPath("$[?(@.id == " + bad.id() + ")].coolingDown")
			.isEqualTo(List.of(true));
	}

	@Test
	void revokedProviderKeyFallsBackAndIsNotBlamedOnTheClient() {
		createProvider("revoked", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		createProvider("backup", ProviderType.OPENAI, backup.baseUrl() + "/v1", 20, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(401)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\":{\"message\":\"Incorrect API key\"}}")));
		backup.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(OK)));

		chat(key, false, 200);

		// Only the revoked one: the client gets 502 explaining it, not a confusing 401
		backup.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(403)));
		assertThat(chat(key, false, 502)).contains("rejected the gateway's API key");
	}

	@Test
	void streamWithoutUsageIsEstimatedInsteadOfFree() {
		createProvider("openai", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		String key = createKey("devA");
		// 400 characters of text and no usage chunk (a provider that ignores stream_options)
		String text = "x".repeat(400);
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
			.withBody("data: {\"choices\":[{\"delta\":{\"content\":\"" + text + "\"}}]}\n\ndata: [DONE]\n\n")));

		chat(key, true, 200);
		recorder.awaitIdle();
		var log = requestLogs.findAll().get(0);
		assertThat(log.getOutputTokens()).isEqualTo(100);
		assertThat(log.getInputTokens()).isPositive();
		assertThat(log.getCost()).isPositive();
		assertThat(log.getErrorMessage()).contains("Usage estimated");
	}

	@Test
	void retryAfterAndRequestIdArePassedToTheClient() {
		createProvider("only", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		String key = createKey("devA");
		primary.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(429)
			.withHeader("Content-Type", "application/json")
			.withHeader("Retry-After", "7")
			.withHeader("x-request-id", "req_123")
			.withHeader("x-ratelimit-remaining-tokens", "0")
			.withBody("{\"error\":{\"message\":\"slow down\"}}")));

		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\":\"gpt-4o\"}")
			.exchange()
			.expectStatus()
			.isEqualTo(429)
			.expectHeader()
			.valueEquals("Retry-After", "7")
			.expectHeader()
			.valueEquals("x-request-id", "req_123")
			.expectHeader()
			.doesNotExist("x-ratelimit-remaining-tokens");
	}

	@Test
	void providerConnectionTest() {
		ProviderResponse p = createProvider("openai", ProviderType.OPENAI, primary.baseUrl() + "/v1", 10, "gpt-4o");
		primary.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/v1/models")
			.willReturn(aResponse().withHeader("Content-Type", "application/json")
				.withBody("{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-4o-mini\"}]}")));
		admin.post()
			.uri("/api/admin/providers/{id}/test", p.id())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.ok")
			.isEqualTo(true)
			.jsonPath("$.models")
			.isEqualTo(List.of("gpt-4o", "gpt-4o-mini"));

		primary.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/v1/models")
			.willReturn(aResponse().withStatus(401)));
		admin.post()
			.uri("/api/admin/providers/{id}/test", p.id())
			.exchange()
			.expectBody()
			.jsonPath("$.ok")
			.isEqualTo(false)
			.jsonPath("$.message")
			.value(m -> assertThat((String) m).contains("API key was rejected"));

		ProviderResponse dead = createProvider("dead", ProviderType.OPENAI, "http://127.0.0.1:1/v1", 10, "gpt-4o");
		admin.post()
			.uri("/api/admin/providers/{id}/test", dead.id())
			.exchange()
			.expectBody()
			.jsonPath("$.ok")
			.isEqualTo(false)
			.jsonPath("$.message")
			.value(m -> assertThat((String) m).startsWith("Could not connect"));
	}

}
