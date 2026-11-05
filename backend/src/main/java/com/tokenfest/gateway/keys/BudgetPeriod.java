package com.tokenfest.gateway.keys;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A calendar month in the configured time zone. Budgets reset at the start of each month.
 */
public record BudgetPeriod(YearMonth month) {

	public static BudgetPeriod current(Clock clock) {
		return new BudgetPeriod(YearMonth.now(clock));
	}

	public LocalDate firstDay() {
		return month.atDay(1);
	}

	public LocalDate lastDay() {
		return month.atEndOfMonth();
	}

	@Override
	public String toString() {
		return month.toString();
	}

}
