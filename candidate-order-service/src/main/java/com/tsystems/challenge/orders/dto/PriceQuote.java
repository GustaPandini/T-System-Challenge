package com.tsystems.challenge.orders.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceQuote(
        String quoteId,
        String productId,
        String country,
        BigDecimal amount,
        String currency,
        Instant validUntil
) {
}
