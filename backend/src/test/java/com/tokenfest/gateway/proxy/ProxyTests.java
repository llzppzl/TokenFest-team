package com.tokenfest.gateway.proxy;

import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tokenfest.gateway.domain.ProviderType;
import com.tokenfest.gateway.keys.KeyRequest;
import com.tokenfest.gateway.provider.ProviderResponse;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

class ProxyTests extends IntegrationTestSupport {

	@RegisterExtension
	static WireMockExtension upstream = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private static final String CHAT_BODY = """
			{"model": "gpt-4o", "messages": [{"role": "user", "content": "Hi"}]}""";

	private static final String CHAT_RESPONSE = """
			{"id": "chatcmpl-1", "object": "chat.completion", "model": "gpt-4o",
			 "choices": [{"index": 0, "message": {"role": "assistant", "content": "Hello!"}, "finish_reason": "stop"}],
			 "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}}""";

	private String openAiBaseUrl() {
		return upstream.baseUrl() + "/v1";
	}

	@Test
	void forwardsOpenAiChatWithProviderKey() {
		createProvider("openai", ProviderType.OPENAI, openAiBaseUrl(), 10, "gpt-4o");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(CHAT_RESPONSE)));

		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(CHAT_BODY)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.choices[0].message.content")
			.isEqualTo("Hello!");

		upstream.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
			.withHeader("Authorization", equalTo("Bearer sk-openai"))
			.withRequestBody(equalToJson(CHAT_BODY)));
	}

	@Test
	void forwardsAnthropicMessagesWithXApiKey() {
		createProvider("anthropic", ProviderType.ANTHROPIC, upstream.baseUrl(), 10, "sonnet-4-5");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/messages").willReturn(aResponse().withHeader("Content-Type", "application/json")
			.withBody("""
					{"id": "msg_1", "type": "message", "role": "assistant",
					 "content": [{"type": "text", "text": "Hi there"}],
					 "usage": {"input_tokens": 12, "output_tokens": 4}}""")));

		client.post()
			.uri("/v1/messages")
			.header("x-api-key", key)
			.header("anthropic-version", "2023-06-01")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("""
					{"model": "sonnet-4-5", "max_tokens": 100, "messages": [{"role": "user", "content": "Hi"}]}""")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.content[0].text")
			.isEqualTo("Hi there");

		upstream.verify(postRequestedFor(urlEqualTo("/v1/messages")).withHeader("x-api-key", equalTo("sk-anthropic"))
			.withHeader("anthropic-version", equalTo("2023-06-01")));
	}

	@Test
	void upstreamClientErrorIsPassedThrough() {
		createProvider("openai", ProviderType.OPENAI, openAiBaseUrl(), 10, "gpt-4o");
		String key = createKey("devA");
		upstream.stubFor(post("/v1/chat/completions").willReturn(aResponse().withStatus(400)
			.withHeader("Content-Type", "application/json")
			.withBody("{\"error\": {\"message\": \"bad messages\"}}")));

		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(CHAT_BODY)
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.error.message")
			.isEqualTo("bad messages");
	}

	@Test
	void invalidKeyIsRejectedInOpenAiFormat() {
		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer tf-wrong")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(CHAT_BODY)
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.error.code")
			.isEqualTo("invalid_api_key");
	}

	@Test
	void invalidKeyIsRejectedInAnthropicFormat() {
		client.post()
			.uri("/v1/messages")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\": \"sonnet\"}")
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.type")
			.isEqualTo("error")
			.jsonPath("$.error.type")
			.isEqualTo("authentication_error");
	}

	@Test
	void unknownModelIsNotFound() {
		createProvider("openai", ProviderType.OPENAI, openAiBaseUrl(), 10, "gpt-4o");
		String key = createKey("devA");
		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("{\"model\": \"gpt-9\"}")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.error.code")
			.isEqualTo("model_not_found");
	}

	@Test
	void keyOnlyUsesAllowedProviders() {
		createProvider("openai-a", ProviderType.OPENAI, openAiBaseUrl(), 10, "gpt-4o");
		ProviderResponse b = createProvider("openai-b", ProviderType.OPENAI, openAiBaseUrl(), 20, "gpt-4o");
		String key = createKey(new KeyRequest("devA", null, null, List.of(b.id()), true));
		upstream.stubFor(post("/v1/chat/completions")
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(CHAT_RESPONSE)));

		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(CHAT_BODY)
			.exchange()
			.expectStatus()
			.isOk();

		upstream.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
			.withHeader("Authorization", equalTo("Bearer sk-openai-b")));
	}

	@Test
	void invalidJsonIsBadRequest() {
		String key = createKey("devA");
		client.post()
			.uri("/v1/chat/completions")
			.header("Authorization", "Bearer " + key)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue("not json")
			.exchange()
			.expectStatus()
			.isBadRequest();
	}

	@Test
	void listsModelsInBothFormats() {
		createProvider("openai", ProviderType.OPENAI, openAiBaseUrl(), 10, "gpt-4o", "gpt-4o-mini");
		createProvider("anthropic", ProviderType.ANTHROPIC, upstream.baseUrl(), 10, "sonnet-4-5");
		String key = createKey("devA");

		client.get()
			.uri("/v1/models")
			.header("Authorization", "Bearer " + key)
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.object")
			.isEqualTo("list")
			.jsonPath("$.data.length()")
			.isEqualTo(2)
			.jsonPath("$.data[0].owned_by")
			.isEqualTo("openai");

		client.get()
			.uri("/v1/models")
			.header("x-api-key", key)
			.header("anthropic-version", "2023-06-01")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.data[0].id")
			.isEqualTo("sonnet-4-5")
			.jsonPath("$.has_more")
			.isEqualTo(false);
	}

}
