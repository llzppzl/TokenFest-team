package com.tokenfest.gateway.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.provider.ProviderResponse;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvExportTests extends IntegrationTestSupport {

	@Autowired
	private UsageDailyRepository usage;

	@Test
	void adminExportsAMonth() {
		ProviderResponse p = createProvider("openai", ProviderType.OPENAI, "http://localhost:1/v1", 10, "gpt-4o");
		Long devA = apiKeyService.create(new KeyRequest("devA", null, null, List.of(), true)).key().id();
		Long evil = apiKeyService.create(new KeyRequest("=HYPERLINK(\"x\"), team", null, null, List.of(), true))
			.key()
			.id();
		save(LocalDate.of(2026, 9, 2), devA, p.id(), "0.50");
		save(LocalDate.of(2026, 9, 1), evil, p.id(), "1.000000");
		save(LocalDate.of(2026, 10, 1), devA, p.id(), "9");

		String csv = admin.get()
			.uri("/api/admin/export?month=2026-09")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.valueMatches("Content-Disposition", ".*tokenfest-usage-2026-09\\.csv.*")
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		assertThat(csv.lines().toList()).containsExactly(CsvExporter.HEADER,
				"2026-09-01,\"'=HYPERLINK(\"\"x\"\"), team\",openai,gpt-4o,1,100,10,1",
				"2026-09-02,devA,openai,gpt-4o,1,100,10,0.5");

		admin.get().uri("/api/admin/export?month=Sept").exchange().expectStatus().isBadRequest();
	}

	private void save(LocalDate day, Long keyId, Long providerId, String cost) {
		UsageDaily row = new UsageDaily(day, keyId, providerId, "gpt-4o");
		row.add(100, 10, new BigDecimal(cost));
		usage.save(row);
	}

	@Test
	void fieldEscaping() {
		assertThat(CsvExporter.field("plain")).isEqualTo("plain");
		assertThat(CsvExporter.field("a,b")).isEqualTo("\"a,b\"");
		assertThat(CsvExporter.field("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
		assertThat(CsvExporter.field("+1")).isEqualTo("'+1");
		assertThat(CsvExporter.parseMonth(null, YearMonth.of(2026, 10))).isEqualTo(YearMonth.of(2026, 10));
		assertThatThrownBy(() -> CsvExporter.parseMonth("10/2026", YearMonth.of(2026, 10)));
	}

}
