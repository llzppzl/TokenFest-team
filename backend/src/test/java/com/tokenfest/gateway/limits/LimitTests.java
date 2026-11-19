package com.tokenfest.gateway.limits;

import java.math.BigDecimal;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class LimitTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension upstream = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private UsageRecorder recorder;

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private ConcurrencyLimiter concurrency;

	// 1M input tokens at $2/M = $2.00 per request
	private static final String EXPENSIVE_RESPONSE = """
			{"choices": [{"message": {"content": "ok"}}],
			 "usage": {"prompt_tokens": 1000000, "completion_tokens": 0}}""";

	@BeforeEach
	void stubProvider() {
		createProvider("openai", ProviderType.OPENAI, upstream.baseUrl() + "/v1", 10, "gpt-4o");
		upstream.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(EXPENSIVE_RESPONSE)));
	}

	private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec chat(String key) {
		return client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\": \"gpt-4o\", \"messages\": []}")
			.exchange();
	}

	@Test
	void requestOverRateLimitGets429WithRetryAfter() {
		String key = createKey(new KeyRequest("devA", null, 3, List.of(), true));
		for (int i = 0; i < 3; i++) {
			chat(key).expectStatus().isOk();
		}
		chat(key).expectStatus()
			.isEqualTo(429)
			.expectHeader()
			.exists("Retry-After")
			.expectBody()
			.jsonPath("$.error.code")
			.isEqualTo("rate_limit_exceeded");

		// Another key has its own limit
		chat(createKey(new KeyRequest("devB", null, 3, List.of(), true))).expectStatus().isOk();
	}

	@Test
	void requestsAreBlockedOnceBudgetIsUsedUp() {
		String key = createKey(new KeyRequest("devA", new BigDecimal("3.00"), null, List.of(), true));

		chat(key).expectStatus().isOk(); // $2
		recorder.awaitIdle();
		chat(key).expectStatus().isOk(); // $4, over budget now
		recorder.awaitIdle();

		chat(key).expectStatus()
			.isEqualTo(429)
			.expectBody()
			.jsonPath("$.error.code")
			.isEqualTo("budget_exceeded")
			.jsonPath("$.error.message")
			.value(message -> assertThat((String) message).contains("$3.00"));

		recorder.awaitIdle();
		assertThat(requestLogs.findAll()).extracting(r -> r.getStatusCode()).containsExactly(200, 200, 429);
		// Blocked before the body is read (so a blocked key can't make the gateway buffer it)
		assertThat(requestLogs.findAll()).extracting(r -> r.getModel()).containsExactly("gpt-4o", "gpt-4o", null);
	}

	@Test
	void concurrentRequestsPerKeyAreCapped() {
		String key = createKey("devA");
		var limiter = new ConcurrencyLimiter(2);
		var info = apiKeyService.authenticate(key).orElseThrow();
		limiter.acquire(info);
		limiter.acquire(info);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> limiter.acquire(info))
			.isInstanceOf(com.tokenfest.gateway.proxy.ProxyException.class)
			.hasMessageContaining("2 requests running");
		limiter.release(info);
		limiter.acquire(info);
		assertThat(limiter.running(info.id())).isEqualTo(2);
	}

	@Test
	void slotsAreReleasedAfterEachRequest() {
		String key = createKey("devA");
		for (int i = 0; i < 30; i++) {
			chat(key).expectStatus().isOk(); // more than the default 20 in a row
		}
		Long id = apiKeyService.authenticate(key).orElseThrow().id();
		com.tokenfest.gateway.support.Waits.until(() -> concurrency.running(id) == 0);
	}

	@Test
	void raisingTheBudgetUnblocksTheKey() {
		var created = apiKeyService.create(new KeyRequest("devA", new BigDecimal("1.00"), null, List.of(), true));
		chat(created.apiKey()).expectStatus().isOk();
		recorder.awaitIdle();
		chat(created.apiKey()).expectStatus().isEqualTo(429);

		apiKeyService.update(created.key().id(),
				new KeyRequest("devA", new BigDecimal("10.00"), null, List.of(), true));
		chat(created.apiKey()).expectStatus().isOk();
	}

	@Test
	void disabledKeyIsRejectedRightAway() {
		var created = apiKeyService.create(new KeyRequest("devA", null, null, List.of(), true));
		chat(created.apiKey()).expectStatus().isOk();
		apiKeyService.update(created.key().id(), new KeyRequest("devA", null, null, List.of(), false));
		chat(created.apiKey()).expectStatus().isUnauthorized();
	}

}
