package com.tokenfest.gateway.integration;

import java.math.BigDecimal;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole flow (proxy, usage, budget, stats, export) on a real PostgreSQL. Runs in CI;
 * skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTests extends IntegrationTestSupport {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@RegisterExtension
	static WireMockExtension upstream = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private UsageRecorder recorder;

	@Test
	void proxyUsageBudgetStatsAndExport() {
		createProvider("openai", ProviderType.OPENAI, upstream.baseUrl() + "/v1", 10, "gpt-4o");
		// $2 per request (1M input tokens at $2/M)
		upstream.stubFor(post("/v1/chat/completions").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"choices\": [], \"usage\": {\"prompt_tokens\": 1000000, \"completion_tokens\": 0}}")));
		String key = createKey(new KeyRequest("devA", new BigDecimal("5"), null, List.of(), true));

		for (int expected : new int[] { 200, 200, 200, 429 }) {
			client.post()
				.uri("/v1/chat/completions")
				.header("Authorization", "Bearer " + key)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"model\": \"gpt-4o\"}")
				.exchange()
				.expectStatus()
				.isEqualTo(expected);
			recorder.awaitIdle();
		}

		admin.get()
			.uri("/api/admin/stats/summary?days=1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.totalCost")
			.isEqualTo(6.0)
			.jsonPath("$.requests")
			.isEqualTo(4)
			.jsonPath("$.errors")
			.isEqualTo(1);

		String csv = admin.get()
			.uri("/api/admin/export")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(csv).contains(",devA,openai,gpt-4o,3,3000000,0,6");
	}

}
