package com.tokenfest.gateway.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.provider.ProviderResponse;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

class StatsTests extends IntegrationTestSupport {

	@Autowired
	private UsageDailyRepository usage;

	@Autowired
	private RequestLogRepository requestLogs;

	private Long devA;

	private Long devB;

	private ProviderResponse openai;

	@BeforeEach
	void data() {
		openai = createProvider("openai", ProviderType.OPENAI, "http://localhost:1/v1", 10, "gpt-4o", "gpt-4o-mini");
		devA = apiKeyService.create(new KeyRequest("devA", null, null, List.of(), true)).key().id();
		devB = apiKeyService.create(new KeyRequest("devB", null, null, List.of(), true)).key().id();

		usage(LocalDate.of(2026, 10, 1), devA, "gpt-4o", "1.50");
		usage(LocalDate.of(2026, 10, 1), devB, "gpt-4o-mini", "0.25");
		usage(LocalDate.of(2026, 10, 2), devA, "gpt-4o-mini", "0.50");
		usage(LocalDate.of(2026, 9, 30), devA, "gpt-4o", "9.00"); // outside the range

		log(devA, "gpt-4o", 200, "2026-10-01T10:00:00Z");
		log(devA, "gpt-4o-mini", 200, "2026-10-02T11:00:00Z");
		log(devB, "gpt-4o-mini", 200, "2026-10-01T12:00:00Z");
		log(devB, "gpt-4o", 429, "2026-10-02T13:00:00Z");
		log(devA, "gpt-4o", 200, "2026-09-30T10:00:00Z"); // outside the range
	}

	private void usage(LocalDate date, Long keyId, String model, String cost) {
		UsageDaily row = new UsageDaily(date, keyId, openai.id(), model);
		row.add(1000, 100, new BigDecimal(cost));
		usage.save(row);
	}

	private void log(Long keyId, String model, int status, String time) {
		RequestLog log = new RequestLog();
		log.setApiKeyId(keyId);
		log.setProviderId(openai.id());
		log.setApiFormat(ProviderType.OPENAI);
		log.setModel(model);
		log.setStatusCode(status);
		log.setCost(status == 200 ? new BigDecimal("0.1") : BigDecimal.ZERO);
		log.setRequestBody("{\"model\":\"" + model + "\"}");
		log.setResponseBody("{\"ok\":true}");
		log.setCreatedAt(Instant.parse(time));
		requestLogs.save(log);
	}

	@Test
	void summaryForRange() {
		admin.get()
			.uri("/api/admin/stats/summary?from=2026-10-01&to=2026-10-02")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.totalCost")
			.isEqualTo(2.25)
			.jsonPath("$.requests")
			.isEqualTo(4)
			.jsonPath("$.errors")
			.isEqualTo(1)
			.jsonPath("$.inputTokens")
			.isEqualTo(3000)
			.jsonPath("$.activeKeys")
			.isEqualTo(2);
	}

	@Test
	void dailyGroupedByKeyAndModel() {
		admin.get()
			.uri("/api/admin/stats/daily?from=2026-10-01&to=2026-10-02&groupBy=key")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.length()")
			.isEqualTo(3)
			.jsonPath("$[0].date")
			.isEqualTo("2026-10-01")
			.jsonPath("$[0].group")
			.isEqualTo("devA")
			.jsonPath("$[0].cost")
			.isEqualTo(1.5)
			.jsonPath("$[1].group")
			.isEqualTo("devB");

		admin.get()
			.uri("/api/admin/stats/daily?from=2026-10-01&to=2026-10-02&groupBy=model")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[?(@.date == '2026-10-01' && @.group == 'gpt-4o-mini')].cost")
			.isEqualTo(0.25);

		admin.get()
			.uri("/api/admin/stats/daily?groupBy=colour")
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	@Test
	void requestLogWithFiltersAndPaging() {
		admin.get()
			.uri("/api/admin/requests?from=2026-10-01&to=2026-10-02&size=2")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.total")
			.isEqualTo(4)
			.jsonPath("$.items.length()")
			.isEqualTo(2)
			.jsonPath("$.items[0].statusCode")
			.isEqualTo(429) // newest first
			.jsonPath("$.items[0].keyName")
			.isEqualTo("devB")
			.jsonPath("$.items[0].providerName")
			.isEqualTo("openai")
			.jsonPath("$.items[0].requestBody")
			.doesNotExist();

		admin.get()
			.uri("/api/admin/requests?from=2026-10-01&to=2026-10-02&keyId={id}&status=success", devA)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.total")
			.isEqualTo(2);

		admin.get()
			.uri("/api/admin/requests?from=2026-10-01&to=2026-10-02&status=error&model=gpt-4o")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.total")
			.isEqualTo(1);
	}

	@Test
	void requestDetailIncludesBodies() {
		Long id = requestLogs.findAll().get(0).getId();
		admin.get()
			.uri("/api/admin/requests/{id}", id)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.request.id")
			.isEqualTo(id)
			.jsonPath("$.requestBody")
			.value(body -> org.assertj.core.api.Assertions.assertThat((String) body).contains("model"))
			.jsonPath("$.responseBody")
			.isEqualTo("{\"ok\":true}");

		admin.get().uri("/api/admin/requests/999999").exchange().expectStatus().isNotFound();
	}

	@Test
	void invalidRangeIsRejected() {
		admin.get()
			.uri("/api/admin/stats/summary?from=2026-10-05&to=2026-10-01")
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	@Test
	void lastNDaysEndsTodayOnTheServer() {
		admin.get()
			.uri("/api/admin/stats/summary?days=30")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.to")
			.isEqualTo(LocalDate.now(ZoneOffset.UTC).toString())
			.jsonPath("$.from")
			.isEqualTo(LocalDate.now(ZoneOffset.UTC).minusDays(29).toString());
		admin.get().uri("/api/admin/stats/summary?days=0").exchange().expectStatus().isBadRequest();
	}

	@Test
	void statsNeedAdminLogin() {
		client.get().uri("/api/admin/stats/summary").exchange().expectStatus().isUnauthorized();
	}

	@Test
	void dayBoundariesUseUtcByDefault() {
		// 2026-10-02T23:30 UTC belongs to 2026-10-02
		log(devA, "gpt-4o", 200, LocalDate.of(2026, 10, 2).atTime(23, 30).toInstant(ZoneOffset.UTC).toString());
		admin.get()
			.uri("/api/admin/requests?from=2026-10-02&to=2026-10-02")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.total")
			.isEqualTo(3);
	}

}
