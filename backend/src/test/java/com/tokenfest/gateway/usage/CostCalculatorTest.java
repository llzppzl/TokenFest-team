package com.tokenfest.gateway.usage;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {
    @Test void calculatesInputAndOutputCost() {
        Usage usage = new Usage(1_000_000, 500_000, 0, 0);
        assertEquals(0, CostCalculator.cost(usage, new BigDecimal("2"), new BigDecimal("4"))
            .compareTo(new BigDecimal("4.000000")));
    }
    @Test void appliesCachePricingFactors() {
        Usage usage = new Usage(0, 0, 1_000_000, 1_000_000);
        assertEquals(0, CostCalculator.cost(usage, new BigDecimal("2"), BigDecimal.ZERO)
            .compareTo(new BigDecimal("2.700000")));
    }
    @Test void zeroUsageHasZeroCost() {
        assertEquals(0, CostCalculator.cost(Usage.ZERO, BigDecimal.TEN, BigDecimal.TEN).signum());
    }
}
