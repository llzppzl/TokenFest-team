package com.tokenfest.gateway.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cost in USD from token counts and prices per 1M tokens.
 *
 * <p>
 * Anthropic prompt caching: writing to the cache costs 1.25x the input price and reading
 * from it costs 0.1x. OpenAI cached tokens are charged at the full input price (a small
 * overestimate).
 */
public final class CostCalculator {

	static final BigDecimal CACHE_WRITE_FACTOR = new BigDecimal("1.25");

	static final BigDecimal CACHE_READ_FACTOR = new BigDecimal("0.1");

	private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

	private CostCalculator() {
	}

	public static BigDecimal cost(Usage usage, BigDecimal inputPrice, BigDecimal outputPrice) {
		BigDecimal total = inputPrice.multiply(BigDecimal.valueOf(usage.input()))
			.add(inputPrice.multiply(CACHE_WRITE_FACTOR).multiply(BigDecimal.valueOf(usage.cacheWrite())))
			.add(inputPrice.multiply(CACHE_READ_FACTOR).multiply(BigDecimal.valueOf(usage.cacheRead())))
			.add(outputPrice.multiply(BigDecimal.valueOf(usage.output())));
		return total.divide(ONE_MILLION, 6, RoundingMode.HALF_UP);
	}

}
