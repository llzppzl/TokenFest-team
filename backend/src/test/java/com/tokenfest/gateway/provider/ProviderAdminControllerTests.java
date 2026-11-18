package com.tokenfest.gateway.provider;

import java.util.List;
import java.util.Map;

import com.tokenfest.gateway.domain.Provider;
import com.tokenfest.gateway.repository.ProviderRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderAdminControllerTests extends IntegrationTestSupport {

	@Autowired
	private ProviderRepository repository;

	private static Map<String, Object> body(String apiKey, List<Map<String, Object>> models) {
		var body = new java.util.HashMap<String, Object>();
		body.put("name", "openai-main");
		body.put("type", "OPENAI");
		body.put("baseUrl", "https://api.openai.com/v1/");
		body.put("apiKey", apiKey);
		body.put("priority", 10);
		body.put("models", models);
		return body;
	}

	private static Map<String, Object> model(String name, double input, double output) {
		return Map.of("modelName", name, "inputPrice", input, "outputPrice", output);
	}

	@Test
	void createListUpdateDelete() {
		ProviderResponse created = admin.post()
			.uri("/api/admin/providers")
			.bodyValue(body("sk-real", List.of(model("gpt-4o", 2.5, 10))))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(ProviderResponse.class)
			.returnResult()
			.getResponseBody();
		assertThat(created.baseUrl()).isEqualTo("https://api.openai.com/v1");
		assertThat(created.hasApiKey()).isTrue();

		admin.get()
			.uri("/api/admin/providers")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[0].name")
			.isEqualTo("openai-main")
			.jsonPath("$[0].models[0].modelName")
			.isEqualTo("gpt-4o")
			.jsonPath("$[0].apiKey")
			.doesNotExist()
			.jsonPath("$[0].apiKeyEncrypted")
			.doesNotExist();

		// Blank apiKey keeps the old one; models are replaced
		String encryptedBefore = repository.findById(created.id()).orElseThrow().getApiKeyEncrypted();
		admin.put()
			.uri("/api/admin/providers/{id}", created.id())
			.bodyValue(body("", List.of(model("gpt-4o", 3, 12), model("gpt-4o-mini", 0.15, 0.6))))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.models.length()")
			.isEqualTo(2)
			.jsonPath("$.models[0].inputPrice")
			.isEqualTo(3);
		Provider updated = repository.findById(created.id()).orElseThrow();
		assertThat(updated.getApiKeyEncrypted()).isEqualTo(encryptedBefore);

		admin.delete().uri("/api/admin/providers/{id}", created.id()).exchange().expectStatus().isNoContent();
		assertThat(repository.count()).isZero();
	}

	@Test
	void changingTheServerNeedsTheApiKeyAgain() {
		ProviderResponse created = admin.post()
			.uri("/api/admin/providers")
			.bodyValue(body("sk-real", List.of()))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(ProviderResponse.class)
			.returnResult()
			.getResponseBody();

		Map<String, Object> moved = body("", List.of());
		moved.put("baseUrl", "https://evil.example.com/v1");
		admin.put()
			.uri("/api/admin/providers/{id}", created.id())
			.bodyValue(moved)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.error")
			.isEqualTo("Enter the API key again when changing the provider's server");

		// A different path on the same server is fine, and so is a new key
		Map<String, Object> samePlace = body("", List.of());
		samePlace.put("baseUrl", "https://api.openai.com/v2");
		admin.put().uri("/api/admin/providers/{id}", created.id()).bodyValue(samePlace).exchange().expectStatus().isOk();
		moved.put("apiKey", "sk-new");
		admin.put().uri("/api/admin/providers/{id}", created.id()).bodyValue(moved).exchange().expectStatus().isOk();
		assertThat(ProviderService.sameOrigin("https://a.com/v1", "https://a.com:443/x")).isTrue();
		assertThat(ProviderService.sameOrigin("https://a.com", "http://a.com")).isFalse();
	}

	@Test
	void apiKeyIsStoredEncrypted() {
		admin.post()
			.uri("/api/admin/providers")
			.bodyValue(body("sk-real", List.of()))
			.exchange()
			.expectStatus()
			.isCreated();
		assertThat(repository.findAll().get(0).getApiKeyEncrypted()).doesNotContain("sk-real");
	}

	@Test
	void validationErrors() {
		Map<String, Object> bad = body("sk", List.of());
		bad.put("baseUrl", "api.openai.com");
		bad.put("name", "");
		admin.post()
			.uri("/api/admin/providers")
			.bodyValue(bad)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.error")
			.isEqualTo("baseUrl must start with http:// or https://, name must not be blank");

		admin.post()
			.uri("/api/admin/providers")
			.bodyValue(body(null, List.of()))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.error")
			.isEqualTo("apiKey is required");
	}

	@Test
	void duplicateNameIsConflict() {
		admin.post().uri("/api/admin/providers").bodyValue(body("sk", List.of())).exchange().expectStatus().isCreated();
		admin.post()
			.uri("/api/admin/providers")
			.bodyValue(body("sk", List.of()))
			.exchange()
			.expectStatus()
			.isEqualTo(HttpStatus.CONFLICT);
	}

	@Test
	void unknownProviderIsNotFound() {
		admin.delete().uri("/api/admin/providers/999").exchange().expectStatus().isNotFound();
	}

}
