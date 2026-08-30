package com.tsystems.challenge.orders.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String productId,
        @Min(1) @Max(100) int quantity,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String country,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency
) {
}
