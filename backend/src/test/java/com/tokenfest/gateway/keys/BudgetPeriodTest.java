package com.tokenfest.gateway.keys;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class BudgetPeriodTest {
    @Test void usesClockToDetermineCurrentMonth() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-15T12:00:00Z"), ZoneOffset.UTC);
        BudgetPeriod period = BudgetPeriod.current(clock);
        assertEquals(YearMonth.of(2026, 2), period.month());
        assertEquals(LocalDate.of(2026, 2, 1), period.firstDay());
        assertEquals(LocalDate.of(2026, 2, 28), period.lastDay());
        assertEquals("2026-02", period.toString());
    }
    @Test void handlesLeapYear() {
        assertEquals(LocalDate.of(2024, 2, 29),
            new BudgetPeriod(YearMonth.of(2024, 2)).lastDay());
    }
}
