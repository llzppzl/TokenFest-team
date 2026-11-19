package com.tokenfest.gateway.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.UsageDaily;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migrations against a real PostgreSQL. Skipped when Docker is not
 * available (it always runs in CI).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class PostgresSchemaTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired
	private ProviderRepository providers;

	@Autowired
	private ApiKeyRepository apiKeys;

	@Autowired
	private UsageDailyRepository usage;

	@Test
	void migrationsWorkOnPostgres() {
		Provider provider = providers.save(RepositoryTests.provider("openai-main", 10));
		ApiKey key = apiKeys.save(RepositoryTests.apiKey("devA", "hash-1"));
		UsageDaily row = new UsageDaily(LocalDate.of(2026, 10, 1), key.getId(), provider.getId(), "gpt-4o");
		row.add(100, 50, new BigDecimal("1.5"));
		usage.save(row);

		assertThat(providers.findByEnabledTrueOrderByPriorityAscIdAsc()).hasSize(1);
		assertThat(usage.sumCost(key.getId(), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
			.isEqualByComparingTo("1.5");
	}

}
