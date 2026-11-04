package com.tokenfest.gateway.auth;

import java.security.SecureRandom;

import com.tokenfest.gateway.config.TokenFestProperties;
import com.tokenfest.gateway.domain.AdminUser;
import com.tokenfest.gateway.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin account when there is none. The password comes from
 * {@code TOKENFEST_ADMIN_PASSWORD}; if that is not set, a random one is generated and
 * printed in the log once.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	private final TokenFestProperties properties;

	public AdminBootstrap(AdminUserRepository admins, PasswordEncoder passwordEncoder,
			TokenFestProperties properties) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (admins.count() > 0) {
			return;
		}
		String username = properties.admin().username();
		String password = properties.admin().password();
		boolean generated = password == null || password.isBlank();
		if (generated) {
			password = randomPassword();
		}
		admins.save(new AdminUser(username, passwordEncoder.encode(password)));
		if (generated) {
			log.warn("Created admin user '{}' with password: {}  (set TOKENFEST_ADMIN_PASSWORD to choose your own)",
					username, password);
		}
		else {
			log.info("Created admin user '{}'", username);
		}
	}

	private static String randomPassword() {
		String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 16; i++) {
			sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return sb.toString();
	}

}
