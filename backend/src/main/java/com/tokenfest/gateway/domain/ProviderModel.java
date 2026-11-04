package com.tokenfest.gateway.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A model served by a provider, with its price in USD per 1M tokens.
 */
@Entity
@Table(name = "provider_models")
public class ProviderModel {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "provider_id")
	private Provider provider;

	@Column(nullable = false)
	private String modelName;

	@Column(nullable = false)
	private BigDecimal inputPrice = BigDecimal.ZERO;

	@Column(nullable = false)
	private BigDecimal outputPrice = BigDecimal.ZERO;

	protected ProviderModel() {
	}

	public ProviderModel(String modelName, BigDecimal inputPrice, BigDecimal outputPrice) {
		this.modelName = modelName;
		this.inputPrice = inputPrice;
		this.outputPrice = outputPrice;
	}

	public Long getId() {
		return id;
	}

	public Provider getProvider() {
		return provider;
	}

	void setProvider(Provider provider) {
		this.provider = provider;
	}

	public String getModelName() {
		return modelName;
	}

	public BigDecimal getInputPrice() {
		return inputPrice;
	}

	public void setInputPrice(BigDecimal inputPrice) {
		this.inputPrice = inputPrice;
	}

	public BigDecimal getOutputPrice() {
		return outputPrice;
	}

	public void setOutputPrice(BigDecimal outputPrice) {
		this.outputPrice = outputPrice;
	}

}
