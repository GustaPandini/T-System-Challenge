package com.tsystems.challenge.orders.service;

import com.tsystems.challenge.orders.domain.PricingFailure;
import com.tsystems.challenge.orders.domain.PricingFailureReason;

import java.time.Duration;
import java.time.Instant;

public class PricingException extends RuntimeException {

    private static final int MAX_DETAIL = 300;

    private final PricingFailureReason reason;
    private final String providerRequestId;
    private final Duration retryAfter;

    public PricingException(PricingFailureReason reason, String detail, String providerRequestId, Duration retryAfter) {
        super(truncate(detail));
        this.reason = reason;
        this.providerRequestId = providerRequestId;
        this.retryAfter = retryAfter;
    }

    private static String truncate(String detail) {
        if (detail == null || detail.length() <= MAX_DETAIL) {
            return detail;
        }
        return detail.substring(0, MAX_DETAIL) + "...";
    }

    public PricingFailureReason reason() {
        return reason;
    }

    public String providerRequestId() {
        return providerRequestId;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public boolean isRetryable() {
        return reason.isRetryable();
    }

    public PricingFailure toFailure(int attempts, Instant lastAttemptAt) {
        return new PricingFailure(reason, getMessage(), providerRequestId, attempts, lastAttemptAt, retryAfter);
    }
}
