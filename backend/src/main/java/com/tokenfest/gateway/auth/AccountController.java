package com.tokenfest.gateway.auth;

import com.tokenfest.gateway.domain.AdminUser;
import com.tokenfest.gateway.repository.AdminUserRepository;
import com.tokenfest.gateway.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/account")
public class AccountController {

	public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {
	}

	private final AdminUserRepository admins;

	private final PasswordEncoder passwordEncoder;

	public AccountController(AdminUserRepository admins, PasswordEncoder passwordEncoder) {
		this.admins = admins;
		this.passwordEncoder = passwordEncoder;
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChange request) {
		String subject = jwt.getSubject();
		if (subject == null || !subject.startsWith("admin:")) {
			throw new ApiException(HttpStatus.FORBIDDEN, "Only admins can change their password here");
		}
		AdminUser admin = admins.findById(Long.valueOf(subject.substring("admin:".length())))
			.orElseThrow(() -> ApiException.notFound("Admin"));
		if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
			throw ApiException.badRequest("Current password is wrong");
		}
		admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		admins.save(admin);
	}

}
