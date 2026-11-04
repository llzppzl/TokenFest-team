package com.tokenfest.gateway.export;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.repository.ProviderRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.web.ApiException;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly usage as CSV: one row per day, key, provider and model.
 */
@Service
public class CsvExporter {

	static final String HEADER = "date,key,provider,model,requests,input_tokens,output_tokens,cost_usd";

	private final UsageDailyRepository usage;

	private final ApiKeyRepository apiKeys;

	private final ProviderRepository providers;

	public CsvExporter(UsageDailyRepository usage, ApiKeyRepository apiKeys, ProviderRepository providers) {
		this.usage = usage;
		this.apiKeys = apiKeys;
		this.providers = providers;
	}

	public static YearMonth parseMonth(String month, YearMonth current) {
		if (month == null || month.isBlank()) {
			return current;
		}
		try {
			return YearMonth.parse(month.trim());
		}
		catch (RuntimeException e) {
			throw ApiException.badRequest("month must look like 2026-10");
		}
	}

	/** All keys, or only {@code keyId} when it is set. */
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> export(YearMonth month, Long keyId) {
		List<UsageDaily> rows = keyId == null ? usage.findByUsageDateBetween(month.atDay(1), month.atEndOfMonth())
				: usage.findByApiKeyIdAndUsageDateBetween(keyId, month.atDay(1), month.atEndOfMonth());
		Map<Long, String> keyNames = apiKeys.findAll()
			.stream()
			.collect(Collectors.toMap(ApiKey::getId, ApiKey::getName));
		Map<Long, String> providerNames = providers.findAll()
			.stream()
			.collect(Collectors.toMap(Provider::getId, Provider::getName));

		StringBuilder csv = new StringBuilder(HEADER).append('\n');
		rows.stream()
			.sorted(Comparator.comparing(UsageDaily::getUsageDate)
				.thenComparing(r -> keyNames.getOrDefault(r.getApiKeyId(), ""))
				.thenComparing(UsageDaily::getModel))
			.forEach(r -> csv.append(r.getUsageDate())
				.append(',')
				.append(field(keyNames.getOrDefault(r.getApiKeyId(), "(deleted)")))
				.append(',')
				.append(field(providerNames.getOrDefault(r.getProviderId(), "(deleted)")))
				.append(',')
				.append(field(r.getModel()))
				.append(',')
				.append(r.getRequestCount())
				.append(',')
				.append(r.getInputTokens())
				.append(',')
				.append(r.getOutputTokens())
				.append(',')
				.append(r.getCost().stripTrailingZeros().toPlainString())
				.append('\n'));

		String filename = "tokenfest-usage-" + month + ".csv";
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
			.body(csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Quotes a field when needed. Text starting with = + - @ gets a leading apostrophe so
	 * spreadsheet apps don't run it as a formula.
	 */
	static String field(String value) {
		String v = value == null ? "" : value;
		if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
			v = "'" + v;
		}
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			v = "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}

}
