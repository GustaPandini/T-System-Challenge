package com.tsystems.challenge.orders.domain;

public enum PricingFailureReason {

    // Retryable: the identical call started working after a few attempts.
    PROVIDER_UNAVAILABLE(true, PricingFailureCategory.PROVIDER_DOWN),      // 503, and any other 5xx
    RATE_LIMITED(true, PricingFailureCategory.PROVIDER_DOWN),              // 429, sends a Retry-After header
    TRANSPORT_ERROR(true, PricingFailureCategory.PROVIDER_DOWN),           // connection closed with no HTTP response
    INCOMPLETE_QUOTE(true, PricingFailureCategory.PROVIDER_RESPONSE),      // 200 without "amount", required by the contract

    // Permanent: repeating the identical call never helps.
    PRODUCT_NOT_FOUND(false, PricingFailureCategory.ORDER_DATA),           // 404 PRODUCT_NOT_FOUND
    INVALID_REQUEST(false, PricingFailureCategory.ORDER_DATA),             // 400 INVALID_REQUEST
    CURRENCY_MISMATCH(false, PricingFailureCategory.PROVIDER_RESPONSE),    // 200 quoting a different currency
    PRODUCT_MISMATCH(false, PricingFailureCategory.PROVIDER_RESPONSE),     // 200 quoting a different product
    REQUEST_REJECTED(false, PricingFailureCategory.PROVIDER_RESPONSE);     // any other 4xx: permanent until proven otherwise

    private final boolean retryable;
    private final PricingFailureCategory category;

    PricingFailureReason(boolean retryable, PricingFailureCategory category) {
        this.retryable = retryable;
        this.category = category;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public PricingFailureCategory category() {
        return category;
    }
}
