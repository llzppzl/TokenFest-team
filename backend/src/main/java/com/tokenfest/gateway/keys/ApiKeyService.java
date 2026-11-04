package com.tokenfest.gateway.keys;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tokenfest.gateway.config.AfterCommit;
import com.tokenfest.gateway.domain.ApiKey;
import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.repository.ProviderRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import com.tokenfest.gateway.web.ApiException;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and checks TokenFest API keys. Keys look like {@code tf-} followed by 40
 * random characters; only their SHA-256 hash is stored.
 */
@Service
public class ApiKeyService {

	static final String KEY_PREFIX = "tf-";

	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

	private static final int RANDOM_CHARS = 40;

	private static final int SHOWN_CHARS = 10;

	private final SecureRandom random = new SecureRandom();

	/** Key hash to key, so the proxy doesn't hit the database on every request. */
	private final Cache<String, Optional<KeyInfo>> cache = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofSeconds(30))
		.maximumSize(10_000)
		.build();

	private final ApiKeyRepository apiKeys;

	private final ProviderRepository providers;

	private final UsageDailyRepository usage;

	private final Clock clock;

	public ApiKeyService(ApiKeyRepository apiKeys, ProviderRepository providers, UsageDailyRepository usage,
			Clock clock) {
		this.apiKeys = apiKeys;
		this.providers = providers;
		this.usage = usage;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<KeyResponse> list() {
		BudgetPeriod period = BudgetPeriod.current(clock);
		return apiKeys.findAllByOrderByIdAsc().stream().map(key -> KeyResponse.from(key, spent(key, period))).toList();
	}

	@Transactional
	public KeyResponse.Created create(KeyRequest request) {
		String rawKey = generateKey();
		ApiKey key = new ApiKey();
		key.setKeyHash(hash(rawKey));
		key.setKeyPrefix(rawKey.substring(0, SHOWN_CHARS));
		apply(key, request);
		ApiKey saved = apiKeys.save(key);
		return new KeyResponse.Created(KeyResponse.from(saved, BigDecimal.ZERO), rawKey);
	}

	@Transactional
	public KeyResponse update(Long id, KeyRequest request) {
		ApiKey key = apiKeys.findWithProvidersById(id).orElseThrow(() -> ApiException.notFound("Key"));
		apply(key, request);
		AfterCommit.run(cache::invalidateAll);
		return KeyResponse.from(apiKeys.save(key), spent(key, BudgetPeriod.current(clock)));
	}

	/** Forgets cached keys, for example after their allowed providers changed. */
	public void invalidateCache() {
		cache.invalidateAll();
	}

	/**
	 * Replaces a leaked or lost key with a new one. Budget, limits and history stay; the
	 * old key stops working right away.
	 */
	@Transactional
	public KeyResponse.Created regenerate(Long id) {
		ApiKey key = apiKeys.findWithProvidersById(id).orElseThrow(() -> ApiException.notFound("Key"));
		String rawKey = generateKey();
		key.setKeyHash(hash(rawKey));
		key.setKeyPrefix(rawKey.substring(0, SHOWN_CHARS));
		AfterCommit.run(cache::invalidateAll);
		ApiKey saved = apiKeys.save(key);
		return new KeyResponse.Created(KeyResponse.from(saved, spent(saved, BudgetPeriod.current(clock))), rawKey);
	}

	@Transactional
	public void delete(Long id) {
		if (!apiKeys.existsById(id)) {
			throw ApiException.notFound("Key");
		}
		apiKeys.deleteById(id);
		AfterCommit.run(cache::invalidateAll);
	}

	/**
	 * Finds the enabled key for a raw key sent by a client. Cached for 30 seconds; changes
	 * made through this service clear the cache. Blocking.
	 */
	public Optional<KeyInfo> authenticate(String rawKey) {
		if (rawKey == null || rawKey.isBlank()) {
			return Optional.empty();
		}
		return cache.get(hash(rawKey.trim()), hash -> apiKeys.findByKeyHash(hash)
			.filter(ApiKey::isEnabled)
			.map(ApiKeyService::toInfo));
	}

	private static KeyInfo toInfo(ApiKey key) {
		return new KeyInfo(key.getId(), key.getName(), key.getMonthlyBudget(), key.getRateLimitPerMinute(),
				key.getAllowedProviders().stream().map(Provider::getId).collect(Collectors.toUnmodifiableSet()));
	}

	/**
	 * Reads the key from {@code Authorization: Bearer ...} (OpenAI SDKs) or
	 * {@code x-api-key} (Anthropic SDKs).
	 */
	public static String extractKey(HttpHeaders headers) {
		String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
		if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return auth.substring(7).trim();
		}
		return headers.getFirst("x-api-key");
	}

	public static String hash(String rawKey) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawKey.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private String generateKey() {
		StringBuilder sb = new StringBuilder(KEY_PREFIX);
		for (int i = 0; i < RANDOM_CHARS; i++) {
			sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
		}
		return sb.toString();
	}

	private void apply(ApiKey key, KeyRequest request) {
		key.setName(request.name().trim());
		key.setMonthlyBudget(request.monthlyBudget());
		key.setRateLimitPerMinute(request.rateLimitPerMinute());
		key.setEnabled(request.enabled() == null || request.enabled());
		List<Long> ids = request.allowedProviderIds() != null ? request.allowedProviderIds() : List.of();
		List<Provider> allowed = providers.findAllById(ids);
		if (allowed.size() != new HashSet<>(ids).size()) {
			throw ApiException.badRequest("Unknown provider id in allowedProviderIds");
		}
		key.getAllowedProviders().clear();
		key.getAllowedProviders().addAll(allowed);
	}

	private BigDecimal spent(ApiKey key, BudgetPeriod period) {
		if (key.getId() == null) {
			return BigDecimal.ZERO;
		}
		return usage.sumCost(key.getId(), period.firstDay(), period.lastDay());
	}

}
