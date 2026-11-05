package com.tokenfest.gateway.stats;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class DateRangeTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC);

    @Test void defaultsToLastSevenInclusiveDays() {
        DateRange range = DateRange.of(null, null, null, CLOCK);
        assertEquals(LocalDate.of(2026, 10, 30), range.from());
        assertEquals(LocalDate.of(2026, 11, 5), range.to());
    }
    @Test void endInstantIsExclusive() {
        DateRange range = DateRange.of(LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2), null, CLOCK);
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), range.startInstant());
        assertEquals(Instant.parse("2026-11-03T00:00:00Z"), range.endInstant());
    }
    @Test void rejectsInvalidDayCountAndReversedRange() {
        assertThrows(RuntimeException.class, () -> DateRange.of(null, null, 0, CLOCK));
        assertThrows(RuntimeException.class, () -> DateRange.of(null, null, 367, CLOCK));
        assertThrows(RuntimeException.class, () -> DateRange.of(
            LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4), null, CLOCK));
    }
}
