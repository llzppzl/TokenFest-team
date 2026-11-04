package com.tokenfest.gateway.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import com.tokenfest.gateway.config.TokenFestProperties;
import com.tokenfest.gateway.crypto.CryptoService;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Issues and checks the JWTs used by the dashboard. Signed with HS256 using a key derived
 * from {@code TOKENFEST_ENCRYPTION_KEY}.
 */
@Service
public class JwtService {

	public static final String ROLE_ADMIN = "ADMIN";

	public static final String ROLE_USER = "USER";

	private final SecretKey key;

	private final JwtEncoder encoder;

	private final Duration ttl;

	private final Clock clock;

	public JwtService(CryptoService crypto, TokenFestProperties properties, Clock clock) {
		this.key = crypto.deriveKey("tokenfest-jwt");
		this.encoder = NimbusJwtEncoder.withSecretKey(key).build();
		this.ttl = properties.sessionTtl();
		this.clock = clock;
	}

	public String issueAdmin(Long adminId, String username) {
		return issue("admin:" + adminId, ROLE_ADMIN, username, null);
	}

	public String issueUser(Long keyId, String name) {
		return issue("key:" + keyId, ROLE_USER, name, keyId);
	}

	private String issue(String subject, String role, String name, Long keyId) {
		Instant now = Instant.now(clock);
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
			.issuer("tokenfest")
			.subject(subject)
			.issuedAt(now)
			.expiresAt(now.plus(ttl))
			.claim("role", role)
			.claim("name", name);
		if (keyId != null) {
			claims.claim("keyId", keyId);
		}
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
	}

	ReactiveJwtDecoder decoder() {
		return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
	}

}
