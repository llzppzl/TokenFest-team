package com.tokenfest.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One proxied request, including the bodies for debugging.
 */
@Entity
@Table(name = "request_logs")
public class RequestLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long apiKeyId;

	/** Provider that answered (or the last one tried). Null if none was tried. */
	private Long providerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProviderType apiFormat;

	private String model;

	private int statusCode;

	private boolean streamed;

	private int inputTokens;

	private int outputTokens;

	@Column(nullable = false)
	private BigDecimal cost = BigDecimal.ZERO;

	private int latencyMs;

	private int attempts = 1;

	private String errorMessage;

	private String requestBody;

	private String responseBody;

	@Column(nullable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public Long getApiKeyId() {
		return apiKeyId;
	}

	public void setApiKeyId(Long apiKeyId) {
		this.apiKeyId = apiKeyId;
	}

	public Long getProviderId() {
		return providerId;
	}

	public void setProviderId(Long providerId) {
		this.providerId = providerId;
	}

	public ProviderType getApiFormat() {
		return apiFormat;
	}

	public void setApiFormat(ProviderType apiFormat) {
		this.apiFormat = apiFormat;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public boolean isStreamed() {
		return streamed;
	}

	public void setStreamed(boolean streamed) {
		this.streamed = streamed;
	}

	public int getInputTokens() {
		return inputTokens;
	}

	public void setInputTokens(int inputTokens) {
		this.inputTokens = inputTokens;
	}

	public int getOutputTokens() {
		return outputTokens;
	}

	public void setOutputTokens(int outputTokens) {
		this.outputTokens = outputTokens;
	}

	public BigDecimal getCost() {
		return cost;
	}

	public void setCost(BigDecimal cost) {
		this.cost = cost;
	}

	public int getLatencyMs() {
		return latencyMs;
	}

	public void setLatencyMs(int latencyMs) {
		this.latencyMs = latencyMs;
	}

	public int getAttempts() {
		return attempts;
	}

	public void setAttempts(int attempts) {
		this.attempts = attempts;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getRequestBody() {
		return requestBody;
	}

	public void setRequestBody(String requestBody) {
		this.requestBody = requestBody;
	}

	public String getResponseBody() {
		return responseBody;
	}

	public void setResponseBody(String responseBody) {
		this.responseBody = responseBody;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
