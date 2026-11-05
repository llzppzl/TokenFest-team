package com.tokenfest.gateway.stats;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.repository.ProviderRepository;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.stats.StatsDtos.DailyRow;
import com.tokenfest.gateway.stats.StatsDtos.Page;
import com.tokenfest.gateway.stats.StatsDtos.RequestDetail;
import com.tokenfest.gateway.stats.StatsDtos.RequestRow;
import com.tokenfest.gateway.stats.StatsDtos.Summary;
import com.tokenfest.gateway.web.ApiException;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numbers for the dashboard. Cost and tokens come from {@code usage_daily}; request and
 * error counts come from {@code request_logs}.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

	public enum GroupBy {
		KEY, MODEL, PROVIDER
	}

	/** Filters for the request log. Null fields are ignored. */
	public record RequestFilter(Long keyId, Long providerId, String model, String status) {
	}

	static final String DELETED = "(deleted)";

	private final UsageDailyRepository usage;

	private final RequestLogRepository requestLogs;

	private final ApiKeyRepository apiKeys;

	private final ProviderRepository providers;

	private final Clock clock;

	public StatsService(UsageDailyRepository usage, RequestLogRepository requestLogs, ApiKeyRepository apiKeys,
			ProviderRepository providers, Clock clock) {
		this.usage = usage;
		this.requestLogs = requestLogs;
		this.apiKeys = apiKeys;
		this.providers = providers;
		this.clock = clock;
	}

	public DateRange range(LocalDate from, LocalDate to, Integer days) {
		return DateRange.of(from, to, days, clock);
	}

	/** Summary for all keys, or for one key when {@code keyId} is set. */
	public Summary summary(DateRange range, Long keyId) {
		List<UsageDaily> rows = usageRows(range, keyId);
		BigDecimal cost = rows.stream().map(UsageDaily::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
		long input = rows.stream().mapToLong(UsageDaily::getInputTokens).sum();
		long output = rows.stream().mapToLong(UsageDaily::getOutputTokens).sum();
		Set<Long> activeKeys = new HashSet<>();
		rows.forEach(r -> activeKeys.add(r.getApiKeyId()));
		RequestFilter all = new RequestFilter(keyId, null, null, null);
		long requests = requestLogs.count(spec(range, all));
		long errors = requestLogs.count(spec(range, new RequestFilter(keyId, null, null, "error")));
		return new Summary(range.from(), range.to(), cost, requests, errors, input, output, activeKeys.size());
	}

	/** One row per day and group, sorted by date then group. */
	public List<DailyRow> daily(DateRange range, GroupBy groupBy, Long keyId) {
		Map<Long, String> keyNames = keyNames();
		Map<Long, String> providerNames = providerNames();
		Function<UsageDaily, String> group = switch (groupBy) {
			case KEY -> r -> keyNames.getOrDefault(r.getApiKeyId(), DELETED);
			case MODEL -> UsageDaily::getModel;
			case PROVIDER -> r -> providerNames.getOrDefault(r.getProviderId(), DELETED);
		};
		Map<LocalDate, Map<String, long[]>> counts = new TreeMap<>();
		Map<LocalDate, Map<String, BigDecimal>> costs = new TreeMap<>();
		for (UsageDaily r : usageRows(range, keyId)) {
			String g = group.apply(r);
			long[] c = counts.computeIfAbsent(r.getUsageDate(), d -> new TreeMap<>())
				.computeIfAbsent(g, x -> new long[3]);
			c[0] += r.getRequestCount();
			c[1] += r.getInputTokens();
			c[2] += r.getOutputTokens();
			costs.computeIfAbsent(r.getUsageDate(), d -> new TreeMap<>()).merge(g, r.getCost(), BigDecimal::add);
		}
		List<DailyRow> result = new ArrayList<>();
		counts.forEach((date, groups) -> groups.forEach((g, c) -> result
			.add(new DailyRow(date, g, costs.get(date).get(g), c[0], c[1], c[2]))));
		return result;
	}

	public Page<RequestRow> requests(DateRange range, RequestFilter filter, int page, int size) {
		int safeSize = Math.clamp(size, 1, 200);
		var result = requestLogs.findAll(spec(range, filter),
				PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
		Map<Long, String> keyNames = keyNames();
		Map<Long, String> providerNames = providerNames();
		List<RequestRow> items = result.getContent().stream().map(r -> row(r, keyNames, providerNames)).toList();
		return new Page<>(items, result.getNumber(), safeSize, result.getTotalElements());
	}

	/** One request with bodies. If {@code keyId} is set, the request must belong to it. */
	public RequestDetail request(Long id, Long keyId) {
		RequestLog log = requestLogs.findById(id)
			.filter(r -> keyId == null || keyId.equals(r.getApiKeyId()))
			.orElseThrow(() -> ApiException.notFound("Request"));
		return new RequestDetail(row(log, keyNames(), providerNames()), log.getRequestBody(), log.getResponseBody());
	}

	private List<UsageDaily> usageRows(DateRange range, Long keyId) {
		return keyId == null ? usage.findByUsageDateBetween(range.from(), range.to())
				: usage.findByApiKeyIdAndUsageDateBetween(keyId, range.from(), range.to());
	}

	private static Specification<RequestLog> spec(DateRange range, RequestFilter filter) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.startInstant()));
			predicates.add(cb.lessThan(root.get("createdAt"), range.endInstant()));
			if (filter.keyId() != null) {
				predicates.add(cb.equal(root.get("apiKeyId"), filter.keyId()));
			}
			if (filter.providerId() != null) {
				predicates.add(cb.equal(root.get("providerId"), filter.providerId()));
			}
			if (filter.model() != null && !filter.model().isBlank()) {
				predicates.add(cb.equal(root.get("model"), filter.model().trim()));
			}
			if ("success".equals(filter.status())) {
				predicates.add(cb.lessThan(root.get("statusCode"), 400));
			}
			else if ("error".equals(filter.status())) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("statusCode"), 400));
			}
			else if (filter.status() != null && filter.status().matches("\\d{3}")) {
				predicates.add(cb.equal(root.get("statusCode"), Integer.parseInt(filter.status())));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static RequestRow row(RequestLog r, Map<Long, String> keyNames, Map<Long, String> providerNames) {
		String providerName = r.getProviderId() == null ? null
				: providerNames.getOrDefault(r.getProviderId(), DELETED);
		return new RequestRow(r.getId(), r.getCreatedAt(), r.getApiKeyId(),
				keyNames.getOrDefault(r.getApiKeyId(), DELETED), r.getProviderId(), providerName, r.getApiFormat(),
				r.getModel(), r.getStatusCode(), r.isStreamed(), r.getInputTokens(), r.getOutputTokens(), r.getCost(),
				r.getLatencyMs(), r.getAttempts(), r.getErrorMessage());
	}

	private Map<Long, String> keyNames() {
		return apiKeys.findAll().stream().collect(Collectors.toMap(ApiKey::getId, ApiKey::getName));
	}

	private Map<Long, String> providerNames() {
		return providers.findAll().stream().collect(Collectors.toMap(Provider::getId, Provider::getName));
	}

}
