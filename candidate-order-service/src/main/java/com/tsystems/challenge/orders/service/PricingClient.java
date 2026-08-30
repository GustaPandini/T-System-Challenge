package com.tsystems.challenge.orders.service;

import com.tsystems.challenge.orders.dto.PriceQuote;

public interface PricingClient {
    PriceQuote fetchQuote(String productId, String country, String currency);
}
