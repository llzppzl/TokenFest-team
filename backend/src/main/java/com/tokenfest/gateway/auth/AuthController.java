package com.tokenfest.gateway.auth;

import com.tokenfest.gateway.domain.AdminUser;
import com.tokenfest.gateway.keys.ApiKeyService;
import com.tokenfest.gateway.keys.KeyInfo;
import com.tokenfest.gateway.repository.AdminUserRepository;
import com.tokenfest.gateway.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record KeyLoginRequest(@NotBlank String apiKey) {
	}

	public record LoginResponse(String token, String role, String name) {
	}

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	private final ApiKeyService apiKeys;

	private final JwtService jwt;

	private final LoginThrottle throttle;

	/** Checked when the username doesn't exist, so both cases take the same time. */
	private final String dummyHash;

	public AuthController(AdminUserRepository admins, PasswordEncoder passwordEncoder, ApiKeyService apiKeys,
			JwtService jwt, LoginThrottle throttle) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
		this.apiKeys = apiKeys;
		this.jwt = jwt;
		this.throttle = throttle;
		this.dummyHash = passwordEncoder.encode("not-a-real-password");
	}

	/** Admin login with username and password. */
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, ServerHttpRequest httpRequest) {
		throttle.check(httpRequest);
		AdminUser admin = admins.findByUsername(request.username().trim()).orElse(null);
		String hash = admin != null ? admin.getPasswordHash() : dummyHash;
		if (!passwordEncoder.matches(request.password(), hash) || admin == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password");
		}
		return new LoginResponse(jwt.issueAdmin(admin.getId(), admin.getUsername()), JwtService.ROLE_ADMIN,
				admin.getUsername());
	}

	/** Team member login with their TokenFest API key. */
	@PostMapping("/key-login")
	public LoginResponse keyLogin(@Valid @RequestBody KeyLoginRequest request, ServerHttpRequest httpRequest) {
		throttle.check(httpRequest);
		KeyInfo key = apiKeys.authenticate(request.apiKey())
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or disabled API key"));
		return new LoginResponse(jwt.issueUser(key.id(), key.name()), JwtService.ROLE_USER, key.name());
	}

}
