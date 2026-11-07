package com.tokenfest.gateway.proxy;

import org.springframework.http.HttpStatus;

/**
 * An error created by the gateway itself (not by a provider). Written to the client in
 * the format of the endpoint it called.
 */
public class ProxyException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final Long retryAfterSeconds;

	public ProxyException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	public ProxyException(HttpStatus status, String code, String message, Long retryAfterSeconds) {
		super(message);
		this.status = status;
		this.code = code;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	static ProxyException invalidKey() {
		return new ProxyException(HttpStatus.UNAUTHORIZED, "invalid_api_key", "Invalid or disabled API key");
	}

	static ProxyException badRequest(String message) {
		return new ProxyException(HttpStatus.BAD_REQUEST, "invalid_request", message);
	}

	static ProxyException modelNotFound(String model) {
		return new ProxyException(HttpStatus.NOT_FOUND, "model_not_found",
				"No provider is available for model '" + model + "'");
	}

	static ProxyException upstream(String message) {
		return new ProxyException(HttpStatus.BAD_GATEWAY, "upstream_error", message);
	}

}
