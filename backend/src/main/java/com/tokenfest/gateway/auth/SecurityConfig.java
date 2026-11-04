package com.tokenfest.gateway.auth;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Two filter chains:
 * <ul>
 * <li>{@code /api/**} (dashboard API) needs a JWT from {@code /api/auth/*}.</li>
 * <li>Everything else, including the proxy endpoints {@code /v1/**}, is open here. The
 * proxy checks TokenFest API keys itself, and its {@code Authorization: Bearer tf-...}
 * header must not be read as a JWT.</li>
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

	@Bean
	@Order(1)
	SecurityWebFilterChain apiSecurity(ServerHttpSecurity http, ReactiveJwtDecoder decoder) {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(
				jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
		return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/**"))
			.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
			.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
			.authorizeExchange(auth -> auth.pathMatchers("/api/info", "/api/auth/**")
				.permitAll()
				.pathMatchers("/api/admin/**")
				.hasRole(JwtService.ROLE_ADMIN)
				.pathMatchers("/api/user/**")
				.hasRole(JwtService.ROLE_USER)
				.anyExchange()
				.denyAll())
			.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtDecoder(decoder)
				.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
			.build();
	}

	@Bean
	@Order(2)
	SecurityWebFilterChain otherSecurity(ServerHttpSecurity http) {
		return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
			.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
			.authorizeExchange(auth -> auth.anyExchange().permitAll())
			.build();
	}

	@Bean
	ReactiveJwtDecoder jwtDecoder(JwtService jwtService) {
		return jwtService.decoder();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
