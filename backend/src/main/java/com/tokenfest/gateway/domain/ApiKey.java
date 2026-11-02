package com.tokenfest.gateway.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A TokenFest key given to one team member. Only the SHA-256 hash of the key is stored.
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String keyHash;

	@Column(nullable = false)
	private String keyPrefix;

	/** USD per month, null means no limit. */
	private BigDecimal monthlyBudget;

	/** Requests per minute, null means no limit. */
	private Integer rateLimitPerMinute;

	private boolean enabled = true;

	@Column(nullable = false)
	private Instant createdAt;

	private Instant lastUsedAt;

	/** Providers this key may use. Empty means all providers. */
	@ManyToMany
	@JoinTable(name = "api_key_providers", joinColumns = @JoinColumn(name = "api_key_id"),
			inverseJoinColumns = @JoinColumn(name = "provider_id"))
	private Set<Provider> allowedProviders = new HashSet<>();

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getKeyHash() {
		return keyHash;
	}

	public void setKeyHash(String keyHash) {
		this.keyHash = keyHash;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public void setKeyPrefix(String keyPrefix) {
		this.keyPrefix = keyPrefix;
	}

	public BigDecimal getMonthlyBudget() {
		return monthlyBudget;
	}

	public void setMonthlyBudget(BigDecimal monthlyBudget) {
		this.monthlyBudget = monthlyBudget;
	}

	public Integer getRateLimitPerMinute() {
		return rateLimitPerMinute;
	}

	public void setRateLimitPerMinute(Integer rateLimitPerMinute) {
		this.rateLimitPerMinute = rateLimitPerMinute;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public void setLastUsedAt(Instant lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

	public Set<Provider> getAllowedProviders() {
		return allowedProviders;
	}

}
