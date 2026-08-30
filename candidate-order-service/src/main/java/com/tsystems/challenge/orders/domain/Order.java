package com.tsystems.challenge.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Order(
        UUID id,
        String customerId,
        String productId,
        int quantity,
        String country,
        String currency,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        OrderStatus status,
        Instant createdAt
) {
}
