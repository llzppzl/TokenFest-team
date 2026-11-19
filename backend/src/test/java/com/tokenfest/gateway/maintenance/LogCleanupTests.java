package com.tokenfest.gateway.maintenance;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LogCleanupTests extends IntegrationTestSupport {

	@Autowired
	private LogCleanup cleanup;

	@Autowired
	private RequestLogRepository requestLogs;

	@Autowired
	private UsageDailyRepository usage;

	@Test
	void deletesOnlyOldRequestLogsAndKeepsDailyTotals() {
		Long keyId = apiKeyService.create(new com.tokenfest.gateway.keys.KeyRequest("devA", null, null,
				java.util.List.of(), true))
			.key()
			.id();
		save(keyId, Instant.now().minus(Duration.ofDays(120)));
		save(keyId, Instant.now().minus(Duration.ofDays(89)));
		save(keyId, Instant.now());
		UsageDaily old = new UsageDaily(LocalDate.now().minusDays(120), keyId, null, "gpt-4o");
		old.add(1, 1, BigDecimal.ONE);
		usage.save(old);

		assertThat(cleanup.deleteOldLogs()).isEqualTo(1);
		assertThat(requestLogs.count()).isEqualTo(2);
		assertThat(usage.count()).isEqualTo(1);
	}

	private void save(Long keyId, Instant createdAt) {
		RequestLog log = new RequestLog();
		log.setApiKeyId(keyId);
		log.setApiFormat(ProviderType.OPENAI);
		log.setStatusCode(200);
		log.setCreatedAt(createdAt);
		requestLogs.save(log);
	}

}
