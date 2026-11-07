package com.tokenfest.gateway.usage;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Dollar amounts for messages. Small amounts keep up to 6 decimals ($0.00045). */
public final class Money {

	private static final BigDecimal CENT = new BigDecimal("0.01");

	private Money() {
	}

	public static String format(BigDecimal amount) {
		if (amount.signum() == 0 || amount.abs().compareTo(CENT) >= 0) {
			return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
		}
		BigDecimal small = amount.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
		return small.signum() == 0 ? "<$0.000001" : "$" + small.toPlainString();
	}

}
