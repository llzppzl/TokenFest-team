package com.tokenfest.gateway.export;

import static org.junit.jupiter.api.Assertions.*;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class CsvExporterTest {
    @Test void returnsDefaultMonthWhenBlank() {
        YearMonth current = YearMonth.of(2026, 11);
        assertEquals(current, CsvExporter.parseMonth(null, current));
        assertEquals(current, CsvExporter.parseMonth("  ", current));
    }
    @Test void parsesValidMonth() {
        assertEquals(YearMonth.of(2026, 10),
            CsvExporter.parseMonth(" 2026-10 ", YearMonth.of(2026, 11)));
    }
    @Test void rejectsInvalidMonth() {
        assertThrows(RuntimeException.class,
            () -> CsvExporter.parseMonth("October 2026", YearMonth.of(2026, 11)));
    }
    @Test void escapesCsvFields() {
        assertEquals("\"a,b\"", CsvExporter.field("a,b"));
        assertEquals("\"a\"\"b\"", CsvExporter.field("a\"b"));
        assertEquals("'=SUM(A1:A2)", CsvExporter.field("=SUM(A1:A2)"));
        assertEquals("", CsvExporter.field(null));
    }
}
