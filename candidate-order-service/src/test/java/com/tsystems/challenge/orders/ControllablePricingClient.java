package com.tsystems.challenge.orders;

import com.tsystems.challenge.orders.domain.PricingFailureReason;
import com.tsystems.challenge.orders.dto.PriceQuote;
import com.tsystems.challenge.orders.service.PricingClient;
import com.tsystems.challenge.orders.service.PricingException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class ControllablePricingClient implements PricingClient {

    public enum Mode { OK, TRANSIENT_FAILURE, PERMANENT_FAILURE }

    private volatile Mode mode = Mode.OK;
    private final AtomicInteger calls = new AtomicInteger();

    public void behaveNormally() {
        this.mode = Mode.OK;
    }

    public void failTemporarily() {
        this.mode = Mode.TRANSIENT_FAILURE;
    }

    public void failPermanently() {
        this.mode = Mode.PERMANENT_FAILURE;
    }

    public int calls() {
        return calls.get();
    }

    public void resetCalls() {
        calls.set(0);
    }

    @Override
    public PriceQuote fetchQuote(String productId, String country, String currency) {
        calls.incrementAndGet();
        return switch (mode) {
            case OK -> new PriceQuote(
                    "quote-test", productId, country,
                    new BigDecimal("19.99"), currency,
                    Instant.now().plusSeconds(300)
            );
            case TRANSIENT_FAILURE -> throw new PricingException(
                    PricingFailureReason.PROVIDER_UNAVAILABLE,
                    "TEST_TRANSIENT: provider is down", "req-test-transient", null
            );
            case PERMANENT_FAILURE -> throw new PricingException(
                    PricingFailureReason.PRODUCT_NOT_FOUND,
                    "TEST_PERMANENT: unknown product", "req-test-permanent", null
            );
        };
    }
}
