package com.tsystems.challenge.orders.web;

import com.tsystems.challenge.orders.dto.CreateOrderRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateOrderForm {
    @NotBlank
    private String customerId = "customer-42";

    @NotBlank
    private String productId = "SKU-1001";

    @Min(1)
    @Max(100)
    private int quantity = 1;

    @NotBlank
    @Pattern(regexp = "[A-Z]{2}")
    private String country = "DE";

    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    private String currency = "EUR";

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public CreateOrderRequest toRequest() {
        return new CreateOrderRequest(customerId, productId, quantity, country, currency);
    }
}
