package com.tokenfest.gateway.stats;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import com.tokenfest.gateway.web.ApiException;

/**
 * Inclusive range of days in the configured time zone. Either {@code from}/{@code to}, or
 * the last {@code days} days ending today (default 7). "Today" is the server's today, so
 * the browser's time zone doesn't matter.
 */
public record DateRange(LocalDate from, LocalDate to, ZoneId zone) {

	static final int MAX_DAYS = 366;

	public static DateRange of(LocalDate from, LocalDate to, Integer days, Clock clock) {
		if (days != null && (days < 1 || days > MAX_DAYS)) {
			throw ApiException.badRequest("days must be between 1 and " + MAX_DAYS);
		}
		LocalDate end = to != null ? to : LocalDate.now(clock);
		LocalDate start = from != null ? from : end.minusDays((days != null ? days : 7) - 1);
		if (start.isAfter(end)) {
			throw ApiException.badRequest("'from' must not be after 'to'");
		}
		if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
			throw ApiException.badRequest("Date range can be at most " + MAX_DAYS + " days");
		}
		return new DateRange(start, end, clock.getZone());
	}

	public Instant startInstant() {
		return from.atStartOfDay(zone).toInstant();
	}

	/** Start of the day after {@code to} (exclusive end). */
	public Instant endInstant() {
		return to.plusDays(1).atStartOfDay(zone).toInstant();
	}

}
