package com.tokenfest.gateway.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.tokenfest.gateway.auth.JwtService;
import com.tokenfest.gateway.config.TokenFestProperties;
import com.tokenfest.gateway.crypto.CryptoService;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security checks. The endpoint list is read from Spring, so new endpoints are covered
 * automatically.
 */
class SecurityTests extends IntegrationTestSupport {

	private static final String PROVIDER_SECRET = "sk-THIS-MUST-NEVER-LEAK-123";

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	@Autowired
	private TokenFestProperties properties;

	@Autowired
	private CryptoService crypto;

	record Endpoint(HttpMethod method, String path) {
	}

	private List<Endpoint> endpoints(String prefix) {
		List<Endpoint> result = new ArrayList<>();
		for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
			info.getPatternsCondition().getPatterns().stream().map(Object::toString).filter(p -> p.startsWith(prefix))
				.forEach(pattern -> info.getMethodsCondition().getMethods().forEach(m -> result
					.add(new Endpoint(HttpMethod.valueOf(m.name()), pattern.replaceAll("\\{[^}]+}", "1")))));
		}
		assertThat(result).as("endpoints under " + prefix).isNotEmpty();
		return result;
	}

	private int status(Endpoint e, String token) {
		var spec = client.method(e.method()).uri(e.path());
		if (token != null) {
			spec = spec.header("Authorization", "Bearer " + token);
		}
		if (e.method() == HttpMethod.POST || e.method() == HttpMethod.PUT) {
			return spec.contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().returnResult(String.class)
				.getStatus()
				.value();
		}
		return spec.exchange().returnResult(String.class).getStatus().value();
	}

	@Test
	void everyAdminEndpointNeedsAnAdminToken() {
		String userToken = jwtService.issueUser(1L, "devA");
		for (Endpoint e : endpoints("/api/admin")) {
			assertThat(status(e, null)).as("no token: " + e).isEqualTo(401);
			assertThat(status(e, userToken)).as("user token: " + e).isEqualTo(403);
		}
	}

	@Test
	void everyUserEndpointNeedsAUserToken() {
		String adminToken = jwtService.issueAdmin(1L, "admin");
		for (Endpoint e : endpoints("/api/user")) {
			assertThat(status(e, null)).as("no token: " + e).isEqualTo(401);
			assertThat(status(e, adminToken)).as("admin token: " + e).isEqualTo(403);
		}
	}

	@Test
	void forgedExpiredAndUnsignedTokensAreRejected() {
		Endpoint keys = new Endpoint(HttpMethod.GET, "/api/admin/keys");

		// Signed with another secret
		CryptoService otherCrypto = new CryptoService(new TokenFestProperties("x", ZoneOffset.UTC,
				Base64.getEncoder().encodeToString(new byte[32]), null, 1000, null, null, null));
		String forged = new JwtService(otherCrypto, properties, Clock.systemUTC()).issueAdmin(1L, "admin");
		assertThat(status(keys, forged)).isEqualTo(401);

		// Expired: issued a day ago with a 12h lifetime
		Clock yesterday = Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC);
		String expired = new JwtService(crypto, properties, yesterday).issueAdmin(1L, "admin");
		assertThat(status(keys, expired)).isEqualTo(401);

		// "alg": "none" with an admin claim
		String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
		String payload = b64("{\"sub\":\"admin:1\",\"role\":\"ADMIN\",\"exp\":" + (Instant.now().getEpochSecond() + 3600) + "}");
		assertThat(status(keys, header + "." + payload + ".")).isEqualTo(401);

		// Real token with the role changed
		String[] parts = jwtService.issueUser(1L, "devA").split("\\.");
		String tampered = parts[0] + "." + b64(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
			.replace("\"USER\"", "\"ADMIN\"")) + "." + parts[2];
		assertThat(status(keys, tampered)).isEqualTo(401);
	}

	@Test
	void providerKeysNeverLeak() {
		providerService.create(new com.tokenfest.gateway.provider.ProviderRequest("dead", ProviderType.OPENAI,
				"http://127.0.0.1:1/v1", PROVIDER_SECRET, 10, true,
				List.of(new com.tokenfest.gateway.provider.ProviderRequest.ModelRequest("gpt-4o",
						java.math.BigDecimal.ONE, java.math.BigDecimal.ONE))));
		String key = createKey("devA");

		// The proxy error and the request log must not contain the upstream key
		String proxyError = client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\": \"gpt-4o\"}")
			.exchange()
			.expectStatus()
			.isEqualTo(502)
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();
		assertThat(proxyError).doesNotContain(PROVIDER_SECRET);

		// No admin GET endpoint returns it either
		Stream<Endpoint> gets = endpoints("/api/admin").stream().filter(e -> e.method() == HttpMethod.GET);
		gets.forEach(e -> {
			String body = admin.get().uri(e.path()).exchange().returnResult(String.class).getResponseBody()
				.collectList()
				.map(chunks -> String.join("", chunks))
				.block();
			assertThat(body).as(e.toString()).doesNotContain(PROVIDER_SECRET).doesNotContain(key);
		});
	}

	@Test
	void loginIsThrottledAfterTenAttempts() {
		for (int i = 0; i < 10; i++) {
			client.post().uri("/api/auth/login").bodyValue(Map.of("username", "x", "password", "y")).exchange()
				.expectStatus()
				.isUnauthorized();
		}
		client.post()
			.uri("/api/auth/login")
			.bodyValue(Map.of("username", "x", "password", "y"))
			.exchange()
			.expectStatus()
			.isEqualTo(429);
		client.post()
			.uri("/api/auth/key-login")
			.bodyValue(Map.of("apiKey", "tf-guess"))
			.exchange()
			.expectStatus()
			.isEqualTo(429);
	}

	@Test
	void securityHeadersAreSet() {
		client.get()
			.uri("/api/info")
			.exchange()
			.expectHeader()
			.valueEquals("X-Content-Type-Options", "nosniff")
			.expectHeader()
			.exists("X-Frame-Options");
	}

	private static String b64(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
