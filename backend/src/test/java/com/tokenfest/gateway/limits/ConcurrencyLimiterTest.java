package com.tokenfest.gateway.limits;

import static org.junit.jupiter.api.Assertions.*;
import com.tokenfest.gateway.keys.KeyInfo;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConcurrencyLimiterTest {
    private static KeyInfo key(long id) {
        return new KeyInfo(id, "test", null, null, Set.of());
    }
    @Test void tracksAndReleasesActiveRequests() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(2);
        KeyInfo key = key(1L);
        assertEquals(0, limiter.running(1L));
        limiter.acquire(key);
        limiter.acquire(key);
        assertEquals(2, limiter.running(1L));
        limiter.release(key);
        assertEquals(1, limiter.running(1L));
    }
    @Test void rejectsRequestsAboveLimitWithoutLeakingSlots() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(1);
        KeyInfo key = key(1L);
        limiter.acquire(key);
        assertThrows(RuntimeException.class, () -> limiter.acquire(key));
        assertEquals(1, limiter.running(1L));
        limiter.release(key);
        assertEquals(0, limiter.running(1L));
    }
    @Test void limitsKeysIndependently() {
        ConcurrencyLimiter limiter = new ConcurrencyLimiter(1);
        limiter.acquire(key(1L));
        limiter.acquire(key(2L));
        assertEquals(1, limiter.running(1L));
        assertEquals(1, limiter.running(2L));
    }
}
