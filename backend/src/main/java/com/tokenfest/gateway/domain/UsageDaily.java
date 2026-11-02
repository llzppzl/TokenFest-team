package com.tokenfest.gateway.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Usage totals for one day, key, provider and model.
 */
@Entity
@Table(name = "usage_daily")
public class UsageDaily {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private LocalDate usageDate;

	@Column(nullable = false)
	private Long apiKeyId;

	private Long providerId;

	@Column(nullable = false)
	private String model;

	private int requestCount;

	private long inputTokens;

	private long outputTokens;

	@Column(nullable = false)
	private BigDecimal cost = BigDecimal.ZERO;

	protected UsageDaily() {
	}

	public UsageDaily(LocalDate usageDate, Long apiKeyId, Long providerId, String model) {
		this.usageDate = usageDate;
		this.apiKeyId = apiKeyId;
		this.providerId = providerId;
		this.model = model;
	}

	public void add(long inputTokens, long outputTokens, BigDecimal cost) {
		this.requestCount++;
		this.inputTokens += inputTokens;
		this.outputTokens += outputTokens;
		this.cost = this.cost.add(cost);
	}

	public Long getId() {
		return id;
	}

	public LocalDate getUsageDate() {
		return usageDate;
	}

	public Long getApiKeyId() {
		return apiKeyId;
	}

	public Long getProviderId() {
		return providerId;
	}

	public String getModel() {
		return model;
	}

	public int getRequestCount() {
		return requestCount;
	}

	public long getInputTokens() {
		return inputTokens;
	}

	public long getOutputTokens() {
		return outputTokens;
	}

	public BigDecimal getCost() {
		return cost;
	}

}
