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
        Instant createdAt,
        PricingFailure pricingFailure
) {

    public Order priced(BigDecimal newUnitPrice) {
        return copy(
                newUnitPrice,
                newUnitPrice.multiply(BigDecimal.valueOf(quantity)),
                OrderStatus.CONFIRMED,
                null
        );
    }

    public Order awaitingPrice(PricingFailure failure) {
        return copy(unitPrice, totalPrice, OrderStatus.PENDING_PRICE, failure);
    }

    public Order pricingFailed(PricingFailure failure) {
        return copy(unitPrice, totalPrice, OrderStatus.PRICING_FAILED, failure);
    }

    private Order copy(BigDecimal newUnitPrice, BigDecimal newTotalPrice, OrderStatus newStatus, PricingFailure failure) {
        return new Order(id, customerId, productId, quantity, country, currency,
                newUnitPrice, newTotalPrice, newStatus, createdAt, failure);
    }
}
