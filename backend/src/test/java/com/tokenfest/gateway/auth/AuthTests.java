package com.tokenfest.gateway.auth;

import java.util.Map;

import com.tokenfest.gateway.domain.AdminUser;
import com.tokenfest.gateway.repository.AdminUserRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTests extends IntegrationTestSupport {

	@Autowired
	private AdminUserRepository admins;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private void createAdmin(String username, String password) {
		admins.save(new AdminUser(username, passwordEncoder.encode(password)));
	}

	@Test
	void adminLoginGivesTokenThatOpensAdminApi() {
		createAdmin("boss", "correct horse");
		AuthController.LoginResponse login = client.post()
			.uri("/api/auth/login")
			.bodyValue(Map.of("username", "boss", "password", "correct horse"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(AuthController.LoginResponse.class)
			.returnResult()
			.getResponseBody();
		assertThat(login.role()).isEqualTo("ADMIN");
		assertThat(login.name()).isEqualTo("boss");

		client.get()
			.uri("/api/admin/keys")
			.header("Authorization", "Bearer " + login.token())
			.exchange()
			.expectStatus()
			.isOk();
	}

	@Test
	void wrongPasswordOrUserIsRejected() {
		createAdmin("boss", "correct horse");
		client.post()
			.uri("/api/auth/login")
			.bodyValue(Map.of("username", "boss", "password", "wrong"))
			.exchange()
			.expectStatus()
			.isUnauthorized()
			.expectBody()
			.jsonPath("$.error")
			.isEqualTo("Wrong username or password");
		client.post()
			.uri("/api/auth/login")
			.bodyValue(Map.of("username", "nobody", "password", "wrong"))
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void adminApiNeedsLogin() {
		client.get().uri("/api/admin/providers").exchange().expectStatus().isUnauthorized();
		client.get()
			.uri("/api/admin/providers")
			.header("Authorization", "Bearer not-a-jwt")
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void teamMemberCanLogInWithKeyButNotUseAdminApi() {
		String key = createKey("devA");
		AuthController.LoginResponse login = client.post()
			.uri("/api/auth/key-login")
			.bodyValue(Map.of("apiKey", key))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(AuthController.LoginResponse.class)
			.returnResult()
			.getResponseBody();
		assertThat(login.role()).isEqualTo("USER");
		assertThat(login.name()).isEqualTo("devA");

		client.get()
			.uri("/api/admin/keys")
			.header("Authorization", "Bearer " + login.token())
			.exchange()
			.expectStatus()
			.isForbidden();

		client.post()
			.uri("/api/auth/key-login")
			.bodyValue(Map.of("apiKey", "tf-wrong"))
			.exchange()
			.expectStatus()
			.isUnauthorized();
	}

	@Test
	void publicEndpointsStayOpen() {
		client.get().uri("/api/info").exchange().expectStatus().isOk();
		client.get().uri("/actuator/health").exchange().expectStatus().isOk();
	}

}
