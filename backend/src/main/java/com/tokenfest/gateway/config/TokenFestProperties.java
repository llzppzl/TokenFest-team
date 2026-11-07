package com.tokenfest.gateway.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code tokenfest.*} in application.yml.
 *
 * @param version app version (filled in by Maven)
 * @param timeZone zone used for days and budget months
 * @param encryptionKey base64 AES-256 key used to encrypt provider API keys (and to
 * derive the JWT signing key)
 * @param upstream timeouts for calls to LLM providers
 * @param maxBodyChars request/response bodies longer than this are cut before they are
 * stored
 * @param admin first admin account, created when there is none
 * @param sessionTtl how long a dashboard login lasts
 * @param alerts how budget alerts are sent
 */
@ConfigurationProperties("tokenfest")
public record TokenFestProperties(String version, @DefaultValue("UTC") ZoneId timeZone, String encryptionKey,
		@DefaultValue Upstream upstream, @DefaultValue("100000") int maxBodyChars, @DefaultValue Admin admin,
		@DefaultValue("12h") Duration sessionTtl, @DefaultValue Alerts alerts) {

	/**
	 * @param connectTimeout time to open a connection to a provider
	 * @param readTimeout longest silence while waiting for or reading a response
	 * @param streamHeadersTimeout for streaming requests, how long to wait for the
	 * provider to start answering before trying the next one
	 */
	public record Upstream(@DefaultValue("10s") Duration connectTimeout, @DefaultValue("300s") Duration readTimeout,
			@DefaultValue("60s") Duration streamHeadersTimeout) {
	}

	public record Admin(@DefaultValue("admin") String username, String password) {
	}

	/**
	 * @param from sender address of alert emails
	 * @param retryBackoff first wait before retrying a failed alert (doubles each time)
	 */
	public record Alerts(@DefaultValue("tokenfest@localhost") String from,
			@DefaultValue("1s") Duration retryBackoff) {
	}

}
