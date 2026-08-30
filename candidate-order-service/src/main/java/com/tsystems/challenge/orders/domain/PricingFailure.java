package com.tsystems.challenge.orders.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record PricingFailure(
        PricingFailureReason reason,
        String detail,
        String providerRequestId,
        int attempts,
        Instant lastAttemptAt,
        Duration retryAfter
) {
    public PricingFailure {
        Objects.requireNonNull(reason, "reason");
    }

    public PricingFailureCategory category() {
        return reason.category();
    }
}
