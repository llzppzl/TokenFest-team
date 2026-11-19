package com.tokenfest.gateway.keys;

import java.util.List;
import java.util.Map;

import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.provider.ProviderResponse;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class KeyAdminControllerTests extends IntegrationTestSupport {

	@Autowired
	private ApiKeyRepository repository;

	@Test
	void createReturnsFullKeyOnceAndStoresOnlyHash() {
		KeyResponse.Created created = admin.post()
			.uri("/api/admin/keys")
			.bodyValue(Map.of("name", "devA", "monthlyBudget", 100, "rateLimitPerMinute", 60))
			.exchange()
			.expectStatus()
			.isCreated()
			.expectBody(KeyResponse.Created.class)
			.returnResult()
			.getResponseBody();

		assertThat(created.apiKey()).startsWith("tf-").hasSize(43);
		assertThat(created.key().keyPrefix()).isEqualTo(created.apiKey().substring(0, 10));
		assertThat(repository.findAll().get(0).getKeyHash()).isEqualTo(ApiKeyService.hash(created.apiKey()));

		admin.get()
			.uri("/api/admin/keys")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$[0].name")
			.isEqualTo("devA")
			.jsonPath("$[0].monthlyBudget")
			.isEqualTo(100)
			.jsonPath("$[0].spentThisMonth")
			.isEqualTo(0)
			.jsonPath("$[0].apiKey")
			.doesNotExist()
			.jsonPath("$[0].keyHash")
			.doesNotExist();
	}

	@Test
	void updateAndDelete() {
		ProviderResponse provider = createProvider("openai", ProviderType.OPENAI, "http://localhost:1/v1", 10,
				"gpt-4o");
		Long id = apiKeyService.create(new KeyRequest("devB", null, null, List.of(), true)).key().id();

		admin.put()
			.uri("/api/admin/keys/{id}", id)
			.bodyValue(Map.of("name", "devB", "enabled", false, "allowedProviderIds", List.of(provider.id())))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.enabled")
			.isEqualTo(false)
			.jsonPath("$.allowedProviderIds[0]")
			.isEqualTo(provider.id());

		admin.delete().uri("/api/admin/keys/{id}", id).exchange().expectStatus().isNoContent();
		assertThat(repository.count()).isZero();
	}

	@Test
	void unknownProviderIdIsRejected() {
		admin.post()
			.uri("/api/admin/keys")
			.bodyValue(Map.of("name", "devA", "allowedProviderIds", List.of(12345)))
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	@Test
	void negativeBudgetIsRejected() {
		admin.post()
			.uri("/api/admin/keys")
			.bodyValue(Map.of("name", "devA", "monthlyBudget", -1))
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	@Test
	void providerCannotBeDeletedWhileAKeyMayOnlyUseIt() {
		ProviderResponse only = createProvider("cheap", ProviderType.OPENAI, "http://localhost:1/v1", 10, "gpt-4o");
		createProvider("expensive", ProviderType.OPENAI, "http://localhost:2/v1", 20, "gpt-4o");
		apiKeyService.create(new KeyRequest("intern", null, null, List.of(only.id()), true));

		admin.delete()
			.uri("/api/admin/providers/{id}", only.id())
			.exchange()
			.expectStatus()
			.isEqualTo(409)
			.expectBody()
			.jsonPath("$.error")
			.value(m -> assertThat((String) m).contains("intern"));
	}

	@Test
	void regenerateReplacesTheSecretButKeepsTheKey() {
		KeyResponse.Created created = apiKeyService.create(new KeyRequest("dan", null, null, List.of(), true));
		KeyResponse.Created regenerated = admin.post()
			.uri("/api/admin/keys/{id}/regenerate", created.key().id())
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(KeyResponse.Created.class)
			.returnResult()
			.getResponseBody();

		assertThat(regenerated.key().id()).isEqualTo(created.key().id());
		assertThat(regenerated.apiKey()).startsWith("tf-").isNotEqualTo(created.apiKey());
		assertThat(apiKeyService.authenticate(created.apiKey())).isEmpty();
		assertThat(apiKeyService.authenticate(regenerated.apiKey())).isPresent();
	}

	@Test
	void authenticateChecksHashAndEnabled() {
		String raw = createKey("devC");
		assertThat(apiKeyService.authenticate(raw)).isPresent();
		assertThat(apiKeyService.authenticate(raw + "x")).isEmpty();
		assertThat(apiKeyService.authenticate(null)).isEmpty();
	}

}
