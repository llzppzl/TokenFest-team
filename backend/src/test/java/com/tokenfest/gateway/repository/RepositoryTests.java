package com.tokenfest.gateway.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.tokenfest.gateway.domain.AlertEvent;
import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.ProviderModel;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.domain.UsageDaily;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks that the entities match the Flyway schema (runs on H2 in PostgreSQL mode).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryTests {

	@Autowired
	private TestEntityManager em;

	@Autowired
	private ProviderRepository providers;

	@Autowired
	private ApiKeyRepository apiKeys;

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageDailyRepository usage;

	@Autowired
	private AlertEventRepository alerts;

	@Test
	void savesProviderWithModels() {
		Provider saved = providers.save(provider("openai-main", 10));
		em.flush();
		em.clear();

		Provider found = providers.findWithModelsById(saved.getId()).orElseThrow();
		assertThat(found.getType()).isEqualTo(ProviderType.OPENAI);
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(found.getModels()).extracting(ProviderModel::getModelName).containsExactly("gpt-4o", "gpt-4o-mini");
		assertThat(found.getModels().get(0).getInputPrice()).isEqualByComparingTo("2.5");
	}

	@Test
	void providersAreOrderedByPriority() {
		providers.save(provider("b", 20));
		providers.save(provider("a", 10));
		Provider disabled = provider("c", 5);
		disabled.setEnabled(false);
		providers.save(disabled);
		em.flush();
		em.clear();

		assertThat(providers.findByEnabledTrueOrderByPriorityAscIdAsc()).extracting(Provider::getName)
			.containsExactly("a", "b");
	}

	@Test
	void findsApiKeyByHashWithAllowedProviders() {
		Provider provider = providers.save(provider("openai-main", 10));
		ApiKey key = apiKey("devA", "hash-1");
		key.setMonthlyBudget(new BigDecimal("100"));
		key.getAllowedProviders().add(provider);
		apiKeys.save(key);
		em.flush();
		em.clear();

		ApiKey found = apiKeys.findByKeyHash("hash-1").orElseThrow();
		assertThat(found.getName()).isEqualTo("devA");
		assertThat(found.getMonthlyBudget()).isEqualByComparingTo("100");
		assertThat(found.getRateLimitPerMinute()).isNull();
		assertThat(found.getAllowedProviders()).extracting(Provider::getName).containsExactly("openai-main");
	}

	@Test
	void storesLargeRequestBodies() {
		ApiKey key = apiKeys.save(apiKey("devA", "hash-1"));
		RequestLog log = new RequestLog();
		log.setApiKeyId(key.getId());
		log.setApiFormat(ProviderType.ANTHROPIC);
		log.setModel("sonnet");
		log.setStatusCode(200);
		log.setRequestBody("x".repeat(200_000));
		log.setCreatedAt(Instant.now());
		Long id = requestLogs.save(log).getId();
		em.flush();
		em.clear();

		assertThat(requestLogs.findById(id).orElseThrow().getRequestBody()).hasSize(200_000);
	}

	@Test
	void sumsCostForDateRange() {
		ApiKey key = apiKeys.save(apiKey("devA", "hash-1"));
		Provider provider = providers.save(provider("openai-main", 10));
		saveUsage(LocalDate.of(2026, 9, 30), key, provider, "5.00");
		saveUsage(LocalDate.of(2026, 10, 1), key, provider, "1.25");
		saveUsage(LocalDate.of(2026, 10, 15), key, provider, "2.50");
		em.flush();

		BigDecimal october = usage.sumCost(key.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		assertThat(october).isEqualByComparingTo("3.75");
		assertThat(usage.sumCost(key.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))).isZero();
	}

	@Test
	void alertCanOnlyBeRecordedOncePerThresholdAndMonth() {
		ApiKey key = apiKeys.save(apiKey("devA", "hash-1"));
		alerts.save(new AlertEvent(key.getId(), "2026-10", 80, new BigDecimal("80"), new BigDecimal("100")));
		em.flush();
		assertThat(alerts.existsByApiKeyIdAndBudgetMonthAndThresholdPercent(key.getId(), "2026-10", 80)).isTrue();

		assertThatThrownBy(() -> {
			alerts.save(new AlertEvent(key.getId(), "2026-10", 80, new BigDecimal("81"), new BigDecimal("100")));
			em.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	private void saveUsage(LocalDate date, ApiKey key, Provider provider, String cost) {
		UsageDaily row = new UsageDaily(date, key.getId(), provider.getId(), "gpt-4o");
		row.add(100, 50, new BigDecimal(cost));
		usage.save(row);
	}

	static Provider provider(String name, int priority) {
		Provider provider = new Provider();
		provider.setName(name);
		provider.setType(ProviderType.OPENAI);
		provider.setBaseUrl("https://api.openai.com/v1");
		provider.setApiKeyEncrypted("encrypted");
		provider.setPriority(priority);
		provider.addModel(new ProviderModel("gpt-4o-mini", new BigDecimal("0.15"), new BigDecimal("0.6")));
		provider.addModel(new ProviderModel("gpt-4o", new BigDecimal("2.5"), new BigDecimal("10")));
		return provider;
	}

	static ApiKey apiKey(String name, String hash) {
		ApiKey key = new ApiKey();
		key.setName(name);
		key.setKeyHash(hash);
		key.setKeyPrefix("tf-abc");
		return key;
	}

}
