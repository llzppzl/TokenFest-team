package com.tokenfest.gateway.auth;

import java.util.Map;

import com.tokenfest.gateway.domain.AdminUser;
import com.tokenfest.gateway.repository.AdminUserRepository;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTests extends IntegrationTestSupport {

	@Autowired
	private AdminUserRepository admins;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void adminCanChangePassword() {
		AdminUser boss = admins.save(new AdminUser("boss", passwordEncoder.encode("old-password")));
		var bossClient = client.mutate()
			.defaultHeader("Authorization", "Bearer " + jwtService.issueAdmin(boss.getId(), "boss"))
			.build();

		bossClient.put()
			.uri("/api/admin/account/password")
			.bodyValue(Map.of("currentPassword", "wrong", "newPassword", "new-password-1"))
			.exchange()
			.expectStatus()
			.isBadRequest();
		bossClient.put()
			.uri("/api/admin/account/password")
			.bodyValue(Map.of("currentPassword", "old-password", "newPassword", "short"))
			.exchange()
			.expectStatus()
			.isBadRequest();
		bossClient.put()
			.uri("/api/admin/account/password")
			.bodyValue(Map.of("currentPassword", "old-password", "newPassword", "new-password-1"))
			.exchange()
			.expectStatus()
			.isNoContent();

		assertThat(passwordEncoder.matches("new-password-1", admins.findByUsername("boss").orElseThrow().getPasswordHash()))
			.isTrue();
	}

}
