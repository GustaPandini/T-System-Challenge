package com.tsystems.challenge.orders.service;

import com.tsystems.challenge.orders.domain.PricingFailureReason;
import com.tsystems.challenge.orders.dto.PriceQuote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class PricingApiClient implements PricingClient {

    private final RestClient restClient;
    private final Clock clock;

    @Autowired
    public PricingApiClient(
            @Value("${pricing.api.url}") String baseUrl,
            @Value("${pricing.api.connect-timeout}") Duration connectTimeout,
            @Value("${pricing.api.read-timeout}") Duration readTimeout
    ) {
        this(baseUrl, connectTimeout, readTimeout, Clock.systemUTC());
    }

    PricingApiClient(String baseUrl, Duration connectTimeout, Duration readTimeout, Clock clock) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(connectTimeout)
                        .withReadTimeout(readTimeout));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.clock = clock;
    }

    @Override
    public PriceQuote fetchQuote(String productId, String country, String currency) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/prices/{productId}")
                            .queryParam("country", country)
                            .queryParam("currency", currency)
                            .build(productId))
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        String requestId = response.getHeaders().getFirst("X-Request-Id");

                        if (!status.is2xxSuccessful()) {
                            throw new PricingException(
                                    reasonFor(status),
                                    describe(status, response.bodyTo(String.class)),
                                    requestId,
                                    retryAfter(response.getHeaders().getFirst("Retry-After"))
                            );
                        }

                        PriceQuote quote;
                        try {
                            quote = response.bodyTo(PriceQuote.class);
                        } catch (RestClientException ex) {
                            throw unusable("unreadable body: " + ex.getMessage(), requestId);
                        }
                        validate(quote, productId, currency, requestId);
                        return quote;
                    });
        } catch (ResourceAccessException ex) {
            throw new PricingException(
                    PricingFailureReason.TRANSPORT_ERROR,
                    "no HTTP response from the provider: " + ex.getMessage(),
                    null,
                    null
            );
        }
    }

    private void validate(PriceQuote quote, String productId, String currency, String requestId) {
        if (quote == null) {
            throw unusable("empty body", requestId);
        }
        if (quote.amount() == null) {
            throw unusable("quote without amount", requestId);
        }
        if (quote.amount().signum() <= 0) {
            throw unusable("amount is not positive: " + quote.amount(), requestId);
        }
        if (!currency.equalsIgnoreCase(quote.currency())) {
            throw new PricingException(
                    PricingFailureReason.CURRENCY_MISMATCH,
                    "requested currency " + currency + ", quoted " + quote.currency(),
                    requestId,
                    null
            );
        }
        if (!productId.equals(quote.productId())) {
            throw new PricingException(
                    PricingFailureReason.PRODUCT_MISMATCH,
                    "requested product " + productId + ", quoted " + quote.productId(),
                    requestId,
                    null
            );
        }
        if (quote.validUntil() == null) {
            throw unusable("quote without validUntil", requestId);
        }
        if (!quote.validUntil().isAfter(Instant.now(clock))) {
            throw unusable("quote already expired at " + quote.validUntil(), requestId);
        }
    }

    private static PricingException unusable(String detail, String requestId) {
        return new PricingException(PricingFailureReason.INCOMPLETE_QUOTE, detail, requestId, null);
    }

    private static PricingFailureReason reasonFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> PricingFailureReason.INVALID_REQUEST;
            case 404 -> PricingFailureReason.PRODUCT_NOT_FOUND;
            case 429 -> PricingFailureReason.RATE_LIMITED;
            case 503 -> PricingFailureReason.PROVIDER_UNAVAILABLE;
            default -> status.is5xxServerError()
                    ? PricingFailureReason.PROVIDER_UNAVAILABLE
                    : PricingFailureReason.REQUEST_REJECTED;
        };
    }

    private static String describe(HttpStatusCode status, String body) {
        if (body == null || body.isBlank()) {
            return "HTTP " + status.value();
        }
        return "HTTP " + status.value() + ": " + body.strip();
    }

    private static Duration retryAfter(String header) {
        if (header == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(header.strip()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
