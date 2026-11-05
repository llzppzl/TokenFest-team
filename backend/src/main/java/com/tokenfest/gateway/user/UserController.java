package com.tokenfest.gateway.user;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.export.CsvExporter;
import com.tokenfest.gateway.limits.SpendTracker;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.stats.StatsDtos.DailyRow;
import com.tokenfest.gateway.stats.StatsDtos.Page;
import com.tokenfest.gateway.stats.StatsDtos.RequestDetail;
import com.tokenfest.gateway.stats.StatsDtos.RequestRow;
import com.tokenfest.gateway.stats.StatsDtos.Summary;
import com.tokenfest.gateway.stats.StatsService;
import com.tokenfest.gateway.stats.StatsService.GroupBy;
import com.tokenfest.gateway.stats.StatsService.RequestFilter;
import com.tokenfest.gateway.web.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API for team members, who log in with their key. Every endpoint only returns data of
 * the logged-in key.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

	public record Me(Long keyId, String name, String keyPrefix, BigDecimal monthlyBudget, BigDecimal spentThisMonth,
			Integer rateLimitPerMinute, String month) {
	}

	private final ApiKeyRepository apiKeys;

	private final SpendTracker spendTracker;

	private final StatsService stats;

	private final CsvExporter exporter;

	private final Clock clock;

	public UserController(ApiKeyRepository apiKeys, SpendTracker spendTracker, StatsService stats, CsvExporter exporter,
			Clock clock) {
		this.apiKeys = apiKeys;
		this.spendTracker = spendTracker;
		this.stats = stats;
		this.exporter = exporter;
		this.clock = clock;
	}

	@GetMapping("/me")
	public Me me(@AuthenticationPrincipal Jwt jwt) {
		ApiKey key = currentKey(jwt);
		return new Me(key.getId(), key.getName(), key.getKeyPrefix(), key.getMonthlyBudget(),
				spendTracker.spentThisMonth(key.getId()), key.getRateLimitPerMinute(),
				YearMonth.now(clock).toString());
	}

	@GetMapping("/stats/summary")
	public Summary summary(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer days) {
		return stats.summary(stats.range(null, null, days), currentKey(jwt).getId());
	}

	@GetMapping("/stats/daily")
	public List<DailyRow> daily(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer days) {
		return stats.daily(stats.range(null, null, days), GroupBy.MODEL, currentKey(jwt).getId());
	}

	@GetMapping("/requests")
	public Page<RequestRow> requests(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Integer days,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
			@RequestParam(required = false) String model, @RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
		Long keyId = currentKey(jwt).getId();
		return stats.requests(stats.range(from, to, days), new RequestFilter(keyId, null, model, status), page, size);
	}

	@GetMapping("/requests/{id}")
	public RequestDetail request(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return stats.request(id, currentKey(jwt).getId());
	}

	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(required = false) String month) {
		return exporter.export(CsvExporter.parseMonth(month, YearMonth.now(clock)), currentKey(jwt).getId());
	}

	/** The key from the login token; rejected if it was deleted or disabled since. */
	private ApiKey currentKey(Jwt jwt) {
		Object claim = jwt.getClaim("keyId");
		if (!(claim instanceof Number keyId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Not logged in with an API key");
		}
		return apiKeys.findById(keyId.longValue())
			.filter(ApiKey::isEnabled)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "This API key was disabled or deleted"));
	}

}
