package com.tokenfest.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A budget threshold that a key reached in a month. Unique per key, month and threshold.
 */
@Entity
@Table(name = "alert_events")
public class AlertEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long apiKeyId;

	/** Month in yyyy-MM format. */
	@Column(nullable = false)
	private String budgetMonth;

	private int thresholdPercent;

	@Column(nullable = false)
	private BigDecimal spent;

	@Column(nullable = false)
	private BigDecimal budget;

	private boolean delivered;

	private String errorMessage;

	@Column(nullable = false)
	private Instant createdAt;

	protected AlertEvent() {
	}

	public AlertEvent(Long apiKeyId, String budgetMonth, int thresholdPercent, BigDecimal spent, BigDecimal budget) {
		this.apiKeyId = apiKeyId;
		this.budgetMonth = budgetMonth;
		this.thresholdPercent = thresholdPercent;
		this.spent = spent;
		this.budget = budget;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getApiKeyId() {
		return apiKeyId;
	}

	public String getBudgetMonth() {
		return budgetMonth;
	}

	public int getThresholdPercent() {
		return thresholdPercent;
	}

	public BigDecimal getSpent() {
		return spent;
	}

	public BigDecimal getBudget() {
		return budget;
	}

	public boolean isDelivered() {
		return delivered;
	}

	public void setDelivered(boolean delivered) {
		this.delivered = delivered;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
