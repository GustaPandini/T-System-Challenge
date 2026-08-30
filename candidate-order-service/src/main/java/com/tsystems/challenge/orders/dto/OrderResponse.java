package com.tsystems.challenge.orders.dto;

import com.tsystems.challenge.orders.domain.Order;
import com.tsystems.challenge.orders.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
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
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.productId(),
                order.quantity(),
                order.country(),
                order.currency(),
                order.unitPrice(),
                order.totalPrice(),
                order.status(),
                order.createdAt()
        );
    }
}
