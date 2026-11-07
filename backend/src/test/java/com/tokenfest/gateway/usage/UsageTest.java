package com.tokenfest.gateway.usage;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UsageTest {
    @Test void countsCachedTokensAsInput() {
        assertEquals(15, new Usage(10, 3, 2, 3).totalInput());
    }
    @Test void estimatesMissingCountsWithoutReducingReportedCounts() {
        Usage result = new Usage(0, 8, 0, 0).withEstimate(9, 4);
        assertEquals(3, result.input());
        assertEquals(8, result.output());
    }
    @Test void maxUsesLargestFieldValues() {
        assertEquals(new Usage(9, 7, 4, 6),
            new Usage(9, 2, 4, 1).max(new Usage(5, 7, 3, 6)));
    }
}
