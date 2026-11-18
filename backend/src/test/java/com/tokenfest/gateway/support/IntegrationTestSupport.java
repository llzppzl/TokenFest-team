package com.tokenfest.gateway.support;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.tokenfest.gateway.auth.JwtService;
import com.tokenfest.gateway.auth.LoginThrottle;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.ApiKeyService;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.provider.ProviderRequest;
import com.tokenfest.gateway.provider.ProviderRequest.ModelRequest;
import com.tokenfest.gateway.provider.ProviderResponse;
import com.tokenfest.gateway.provider.ProviderService;
import com.tokenfest.gateway.usage.UsageRecorder;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Base class for tests that start the whole app. Empties the database before each test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public abstract class IntegrationTestSupport {

	private static final List<String> TABLES = List.of("alert_events", "usage_daily", "request_logs",
			"api_key_providers", "api_keys", "provider_models", "providers", "app_settings", "admin_users");

	/** Client without login (proxy calls, login endpoints). */
	@Autowired
	protected WebTestClient client;

	/** Client logged in as admin. */
	protected WebTestClient admin;

	@Autowired
	protected JwtService jwtService;

	@Autowired
	protected ProviderService providerService;

	@Autowired
	protected ApiKeyService apiKeyService;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private LoginThrottle loginThrottle;

	@Autowired
	private UsageRecorder usageRecorder;

	@BeforeEach
	void cleanDatabase() {
		// Let the previous test's background writes finish first
		usageRecorder.awaitIdle();
		TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
		loginThrottle.reset();
		admin = client.mutate()
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueAdmin(1L, "admin"))
			.build();
	}

	protected ProviderResponse createProvider(String name, ProviderType type, String baseUrl, int priority,
			String... models) {
		List<ModelRequest> modelRequests = Arrays.stream(models)
			.map(m -> new ModelRequest(m, new BigDecimal("2"), new BigDecimal("8")))
			.toList();
		return providerService.create(new ProviderRequest(name, type, baseUrl, "sk-" + name, priority, true,
				modelRequests));
	}

	/** Creates a key and returns the full key string. */
	protected String createKey(String name) {
		return createKey(new KeyRequest(name, null, null, List.of(), true));
	}

	protected String createKey(KeyRequest request) {
		return apiKeyService.create(request).apiKey();
	}

}
