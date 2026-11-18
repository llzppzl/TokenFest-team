package com.tokenfest.gateway.web;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class InfoControllerTests {

	@Autowired
	private WebTestClient client;

	@Test
	void infoReturnsNameAndVersion() {
		client.get().uri("/api/info").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.name").isEqualTo("TokenFest")
			.jsonPath("$.version").isNotEmpty();
	}

	@Test
	void healthIsUp() {
		client.get().uri("/actuator/health").exchange()
			.expectStatus().isOk()
			.expectBody()
			.jsonPath("$.status").isEqualTo("UP");
	}

}
