package com.tokenfest.gateway.user;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiTests extends IntegrationTestSupport {

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageDailyRepository usage;

	private Long aliceId;

	private Long bobRequestId;

	private WebTestClient devA;

	@BeforeEach
	void data() {
		ProviderResponse openai = createProvider("openai", ProviderType.OPENAI, "http://localhost:1/v1", 10, "gpt-4o");
		var a = apiKeyService.create(new KeyRequest("devA", new BigDecimal("50"), 30, List.of(), true));
		var b = apiKeyService.create(new KeyRequest("devB", null, null, List.of(), true));
		aliceId = a.key().id();
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		saveUsage(today, aliceId, openai.id(), "1.25");
		saveUsage(today, b.key().id(), openai.id(), "7.00");
		saveLog(aliceId, openai.id());
		bobRequestId = saveLog(b.key().id(), openai.id());

		String token = client.post()
			.uri("/api/auth/key-login")
			.bodyValue(Map.of("apiKey", a.apiKey()))
			.exchange()
			.expectBody(Map.class)
			.returnResult()
			.getResponseBody()
			.get("token")
			.toString();
		devA = client.mutate().defaultHeader("Authorization", "Bearer " + token).build();
	}

	private void saveUsage(LocalDate day, Long keyId, Long providerId, String cost) {
		UsageDaily row = new UsageDaily(day, keyId, providerId, "gpt-4o");
		row.add(100, 10, new BigDecimal(cost));
		usage.save(row);
	}

	private Long saveLog(Long keyId, Long providerId) {
		RequestLog log = new RequestLog();
		log.setApiKeyId(keyId);
		log.setProviderId(providerId);
		log.setApiFormat(ProviderType.OPENAI);
		log.setModel("gpt-4o");
		log.setStatusCode(200);
		log.setRequestBody("secret of key " + keyId);
		log.setCreatedAt(Instant.now());
		return requestLogs.save(log).getId();
	}

	@Test
	void meShowsOwnBudgetAndSpending() {
		devA.get()
			.uri("/api/user/me")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.name")
			.isEqualTo("devA")
			.jsonPath("$.monthlyBudget")
			.isEqualTo(50)
			.jsonPath("$.spentThisMonth")
			.isEqualTo(1.25)
			.jsonPath("$.rateLimitPerMinute")
			.isEqualTo(30)
			.jsonPath("$.month")
			.isEqualTo(YearMonth.now(ZoneOffset.UTC).toString());
	}

	@Test
	void onlyOwnRequestsAndStats() {
		devA.get()
			.uri("/api/user/requests")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.total")
			.isEqualTo(1)
			.jsonPath("$.items[0].keyName")
			.isEqualTo("devA");

		devA.get().uri("/api/user/requests/{id}", bobRequestId).exchange().expectStatus().isNotFound();

		devA.get()
			.uri("/api/user/stats/summary?days=7")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.totalCost")
			.isEqualTo(1.25);

		devA.get()
			.uri("/api/user/stats/daily?days=7")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.length()")
			.isEqualTo(1)
			.jsonPath("$[0].group")
			.isEqualTo("gpt-4o");
	}

	@Test
	void exportContainsOnlyOwnRows() {
		String csv = devA.get()
			.uri("/api/user/export")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.parseMediaType("text/csv"))
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(csv.lines()).hasSize(2);
		assertThat(csv).contains(",devA,openai,gpt-4o,1,100,10,1.25").doesNotContain("devB");
	}

	@Test
	void userTokenCannotUseAdminApiAndAdminCannotUseUserApi() {
		devA.get().uri("/api/admin/keys").exchange().expectStatus().isForbidden();
		admin.get().uri("/api/user/me").exchange().expectStatus().isForbidden();
	}

	@Test
	void disabledKeyLosesAccessRightAway() {
		apiKeyService.update(aliceId, new KeyRequest("devA", null, null, List.of(), false));
		devA.get().uri("/api/user/me").exchange().expectStatus().isUnauthorized();
	}

}
