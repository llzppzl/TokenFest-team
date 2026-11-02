package com.tokenfest.gateway.usage;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published on the usage writer thread after a request has been saved.
 */
public record UsageRecordedEvent(Long apiKeyId, BigDecimal cost, Instant createdAt) {
}
