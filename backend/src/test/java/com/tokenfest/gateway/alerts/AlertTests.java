package com.tokenfest.gateway.alerts;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.AlertEvent;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.repository.AlertEventRepository;
import com.tokenfest.gateway.settings.SettingsService;
import com.tokenfest.gateway.settings.SettingsService.AlertSettings;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import com.tokenfest.gateway.support.Waits;
import com.tokenfest.gateway.usage.UsageRecordedEvent;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.BeforeEach;
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

class AlertTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension llm = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@RegisterExtension
	static WireMockExtension hook = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private SettingsService settings;

	@Autowired
	private UsageRecorder recorder;

	@Autowired
	private AlertEventRepository alertEvents;

	@Autowired
	private AlertService alertService;

	@BeforeEach
	void setUp() {
		createProvider("openai", ProviderType.OPENAI, llm.baseUrl() + "/v1", 10, "gpt-4o");
		// 1M input tokens at $2/M: every request costs $2
		llm.stubFor(post("/v1/chat/completions").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("{\"choices\": [], \"usage\": {\"prompt_tokens\": 1000000, \"completion_tokens\": 0}}")));
		hook.stubFor(post("/hook").willReturn(aResponse().withStatus(200)));
		settings.updateAlertSettings(new AlertSettings(List.of(50, 80, 100), hook.baseUrl() + "/hook", List.of()));
	}

	private void chat(String key) {
		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\": \"gpt-4o\", \"messages\": []}")
			.exchange()
			.expectStatus()
			.isOk();
		recorder.awaitIdle();
	}

	private int webhookCalls() {
		return hook.findAll(postRequestedFor(urlEqualTo("/hook"))).size();
	}

	@Test
	void oneAlertPerThreshold() {
		var created = apiKeyService.create(new KeyRequest("devA", new BigDecimal("10"), null, List.of(), true));
		String key = created.apiKey();

		chat(key); // $2 = 20%
		chat(key); // $4 = 40%
		assertThat(webhookCalls()).isZero();

		chat(key); // $6 = 60% -> 50% alert
		Waits.until(() -> webhookCalls() == 1);
		chat(key); // $8 = 80% -> 80% alert
		Waits.until(() -> webhookCalls() == 2);
		chat(key); // $10 = 100% -> 100% alert
		Waits.until(() -> webhookCalls() == 3);

		hook.verify(postRequestedFor(urlEqualTo("/hook"))
			.withRequestBody(matchingJsonPath("$.thresholdPercent", com.github.tomakehurst.wiremock.client.WireMock.equalTo("100")))
			.withRequestBody(matchingJsonPath("$.text", com.github.tomakehurst.wiremock.client.WireMock.containing("devA"))));
		Waits.until(() -> alertEvents.findAll().stream().allMatch(AlertEvent::isDelivered));
		assertThat(alertEvents.findAll()).extracting(AlertEvent::getThresholdPercent).containsExactlyInAnyOrder(50, 80, 100);

		// Same month again (e.g. after a restart): no new alerts
		recorder.callOnWriter(() -> {
			alertService.onUsageRecorded(new UsageRecordedEvent(created.key().id(), new BigDecimal("0.5"), Instant.now()));
			return null;
		});
		assertThat(webhookCalls()).isEqualTo(3);
		assertThat(alertEvents.count()).isEqualTo(3);
	}

	@Test
	void jumpingOverSeveralThresholdsSendsOnlyTheHighest() {
		String key = createKey(new KeyRequest("devB", new BigDecimal("1.50"), null, List.of(), true));
		chat(key); // $2 = 133%

		Waits.until(() -> webhookCalls() == 1);
		hook.verify(postRequestedFor(urlEqualTo("/hook"))
			.withRequestBody(matchingJsonPath("$.thresholdPercent", com.github.tomakehurst.wiremock.client.WireMock.equalTo("100"))));
		assertThat(alertEvents.count()).isEqualTo(3);
	}

	@Test
	void keysWithoutBudgetGetNoAlerts() {
		String key = createKey("devC");
		chat(key);
		assertThat(alertEvents.count()).isZero();
	}

	@Test
	void failedWebhookIsRetriedAndMarkedUndelivered() {
		hook.stubFor(post("/hook").willReturn(aResponse().withStatus(500)));
		String key = createKey(new KeyRequest("devD", new BigDecimal("2"), null, List.of(), true));
		chat(key); // 100%

		Waits.until(() -> alertEvents.findAll().stream().anyMatch(e -> e.getErrorMessage() != null));
		assertThat(webhookCalls()).isEqualTo(3); // first try + 2 retries
		AlertEvent event = alertEvents.findAll().get(0);
		assertThat(event.isDelivered()).isFalse();
		assertThat(event.getErrorMessage()).contains("webhook failed");
	}

	@Test
	void alertHistoryAndTestAlertEndpoints() {
		String key = createKey(new KeyRequest("erin", new BigDecimal("2"), null, List.of(), true));
		chat(key);
		Waits.until(() -> webhookCalls() == 1);

		admin.get()
			.uri("/api/admin/alerts")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[0].keyName")
			.isEqualTo("erin");

		admin.post()
			.uri("/api/admin/settings/test-alert")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.webhook")
			.isEqualTo("sent")
			.jsonPath("$.email")
			.isEqualTo("not configured");
		hook.verify(postRequestedFor(urlEqualTo("/hook"))
			.withRequestBody(matchingJsonPath("$.test", com.github.tomakehurst.wiremock.client.WireMock.equalTo("true"))));
	}

}
