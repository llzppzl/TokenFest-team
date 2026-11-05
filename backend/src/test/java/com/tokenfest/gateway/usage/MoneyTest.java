package com.tokenfest.gateway.usage;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test void formatsWholeDollarsAndCents() {
        assertEquals("$12.35", Money.format(new BigDecimal("12.345")));
        assertEquals("$0.00", Money.format(BigDecimal.ZERO));
    }
    @Test void retainsPrecisionForSmallAmounts() {
        assertEquals("$0.00045", Money.format(new BigDecimal("0.000450")));
    }
    @Test void marksAmountsTooSmallToDisplay() {
        assertEquals("<$0.000001", Money.format(new BigDecimal("0.0000001")));
    }
}
