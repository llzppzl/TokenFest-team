package com.tokenfest.gateway.provider;

import java.math.BigDecimal;
import java.util.List;

import com.tokenfest.gateway.domain.ProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or updating a provider. On update, a blank {@code apiKey} keeps the
 * current key.
 */
public record ProviderRequest(@NotBlank @Size(max = 100) String name, @NotNull ProviderType type,
		@NotBlank @Pattern(regexp = "https?://.+", message = "must start with http:// or https://") String baseUrl,
		String apiKey, Integer priority, Boolean enabled, List<@Valid ModelRequest> models) {

	public record ModelRequest(@NotBlank @Size(max = 200) String modelName,
			@NotNull @PositiveOrZero BigDecimal inputPrice, @NotNull @PositiveOrZero BigDecimal outputPrice) {
	}

}
