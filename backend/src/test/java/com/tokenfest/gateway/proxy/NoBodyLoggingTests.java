package com.tokenfest.gateway.proxy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "tokenfest.log-bodies=false")
class NoBodyLoggingTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension upstream = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageRecorder recorder;

	@Test
	void bodiesAreNotStoredButUsageIs() {
		createProvider("openai", ProviderType.OPENAI, upstream.baseUrl() + "/v1", 10, "gpt-4o");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/chat/completions").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"secret\":\"answer\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}")));

		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"private prompt\"}]}")
			.exchange()
			.expectStatus()
			.isOk();

		recorder.awaitIdle();
		RequestLog log = requestLogs.findAll().get(0);
		assertThat(log.getRequestBody()).isNull();
		assertThat(log.getResponseBody()).isNull();
		assertThat(log.getInputTokens()).isEqualTo(10);
	}

}
