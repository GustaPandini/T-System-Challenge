package com.tsystems.challenge.orders.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class LocalCatalogPriceService {
    private static final Map<String, BigDecimal> PRICES = Map.of(
            "SKU-1001", new BigDecimal("19.99"),
            "SKU-1002", new BigDecimal("29.50"),
            "SKU-1003", new BigDecimal("7.25"),
            "SKU-1004", new BigDecimal("49.90"),
            "SKU-1005", new BigDecimal("15.75"),
            "SKU-1006", new BigDecimal("11.99"),
            "SKU-1007", new BigDecimal("89.00"),
            "SKU-1008", new BigDecimal("5.49")
    );

    public BigDecimal priceFor(String productId) {
        BigDecimal price = PRICES.get(productId);
        if (price == null) {
            throw new IllegalArgumentException("Unknown product: " + productId);
        }
        return price;
    }
}
