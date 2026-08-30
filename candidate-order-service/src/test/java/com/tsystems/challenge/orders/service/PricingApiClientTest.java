package com.tsystems.challenge.orders.service;

import com.sun.net.httpserver.HttpServer;
import com.tsystems.challenge.orders.domain.PricingFailureReason;
import com.tsystems.challenge.orders.dto.PriceQuote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingApiClientTest {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String FUTURE = "2026-08-23T12:05:00Z";

    private HttpServer server;
    private PricingApiClient client;

    private volatile int responseStatus = 200;
    private volatile String responseBody = "";
    private volatile long delayMillis = 0;
    private final Map<String, String> responseHeaders = new LinkedHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Request-Id", "req-abc123");
            responseHeaders.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();

        client = clientFor("http://" + InetAddress.getLoopbackAddress().getHostAddress()
                + ":" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static PricingApiClient clientFor(String baseUrl) {
        return new PricingApiClient(baseUrl, Duration.ofMillis(500), Duration.ofMillis(500), FIXED_CLOCK);
    }

    private void respond(int status, String body) {
        this.responseStatus = status;
        this.responseBody = body;
    }

    private PriceQuote fetch() {
        return client.fetchQuote("SKU-1001", "DE", "EUR");
    }

    private void assertFailsWith(PricingFailureReason expected) {
        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.reason()).isEqualTo(expected));
    }

    private static String quote(String productId, String amountField, String currency, String validUntil) {
        return "{\"quoteId\":\"quote-1\",\"productId\":\"" + productId + "\",\"country\":\"DE\","
                + amountField + "\"currency\":\"" + currency + "\",\"validUntil\":\"" + validUntil + "\"}";
    }

    private static String amount(String value) {
        return "\"amount\":\"" + value + "\",";
    }

    @Test
    void returnsTheQuoteWhenTheProviderAnswersProperly() {
        respond(200, quote("SKU-1001", amount("19.99"), "EUR", FUTURE));

        PriceQuote result = fetch();

        assertThat(result.amount()).isEqualByComparingTo("19.99");
        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.validUntil()).isEqualTo(Instant.parse(FUTURE));
    }

    @Test
    void mapsNotFoundToPermanentProductNotFound() {
        respond(404, "{\"error\":\"PRODUCT_NOT_FOUND\"}");

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(PricingFailureReason.PRODUCT_NOT_FOUND);
                    assertThat(ex.isRetryable()).isFalse();
                    assertThat(ex.getMessage()).contains("404", "PRODUCT_NOT_FOUND");
                });
    }

    @Test
    void mapsBadRequestToInvalidRequest() {
        respond(400, "{\"error\":\"INVALID_REQUEST\"}");
        assertFailsWith(PricingFailureReason.INVALID_REQUEST);
    }

    @Test
    void mapsServiceUnavailableToRetryableProviderUnavailable() {
        respond(503, "{\"error\":\"PROVIDER_TEMPORARILY_UNAVAILABLE\"}");

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(PricingFailureReason.PROVIDER_UNAVAILABLE);
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    void keepsTheRetryAfterAskedByTheProviderOnRateLimit() {
        responseHeaders.put("Retry-After", "2");
        respond(429, "{\"error\":\"RATE_LIMITED\"}");

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(PricingFailureReason.RATE_LIMITED);
                    assertThat(ex.retryAfter()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void treatsAnUnmappedServerErrorAsRetryable() {
        respond(500, "boom");
        assertFailsWith(PricingFailureReason.PROVIDER_UNAVAILABLE);
    }

    @Test
    void treatsAnUnmappedClientErrorAsPermanent() {
        respond(418, "teapot");
        assertFailsWith(PricingFailureReason.REQUEST_REJECTED);
    }

    @Test
    void keepsTheProviderRequestIdForSupportCorrelation() {
        respond(503, "{\"error\":\"PROVIDER_TEMPORARILY_UNAVAILABLE\"}");

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class,
                        ex -> assertThat(ex.providerRequestId()).isEqualTo("req-abc123"));
    }

    @Test
    void rejectsAQuoteWithoutAmount() {
        respond(200, quote("SKU-1001", "", "EUR", FUTURE));
        assertFailsWith(PricingFailureReason.INCOMPLETE_QUOTE);
    }

    @Test
    void rejectsANonPositiveAmount() {
        respond(200, quote("SKU-1001", amount("0.00"), "EUR", FUTURE));
        assertFailsWith(PricingFailureReason.INCOMPLETE_QUOTE);
    }

    @Test
    void rejectsAQuoteInAnotherCurrency() {
        respond(200, quote("SKU-1001", amount("21.49"), "USD", FUTURE));

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(PricingFailureReason.CURRENCY_MISMATCH);
                    assertThat(ex.isRetryable()).isFalse();
                });
    }

    @Test
    void rejectsAQuoteForAnotherProduct() {
        respond(200, quote("SKU-9999", amount("19.99"), "EUR", FUTURE));
        assertFailsWith(PricingFailureReason.PRODUCT_MISMATCH);
    }

    @Test
    void rejectsAQuoteThatIsAlreadyExpired() {
        respond(200, quote("SKU-1001", amount("19.99"), "EUR", "2026-08-23T11:00:00Z"));
        assertFailsWith(PricingFailureReason.INCOMPLETE_QUOTE);
    }

    @Test
    void rejectsABodyThatCannotBeRead() {
        respond(200, "{ this is not json");
        assertFailsWith(PricingFailureReason.INCOMPLETE_QUOTE);
    }

    @Test
    void truncatesAnOversizedErrorBody() {
        respond(500, "x".repeat(5000));

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class, ex -> {
                    assertThat(ex.getMessage()).hasSizeLessThan(400);
                    assertThat(ex.getMessage()).endsWith("...");
                });
    }

    @Test
    void mapsAReadTimeoutToTransportError() {
        delayMillis = 1500;
        respond(200, quote("SKU-1001", amount("19.99"), "EUR", FUTURE));

        assertThatThrownBy(this::fetch)
                .isInstanceOfSatisfying(PricingException.class, ex -> {
                    assertThat(ex.reason()).isEqualTo(PricingFailureReason.TRANSPORT_ERROR);
                    assertThat(ex.isRetryable()).isTrue();
                });
    }

    @Test
    void mapsAConnectionClosedWithoutResponseToTransportError() throws Exception {
        try (ServerSocket rudeServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread accepter = new Thread(() -> {
                try (Socket socket = rudeServer.accept()) {
                    socket.setSoLinger(true, 0);
                } catch (IOException ignored) {
                }
            });
            accepter.setDaemon(true);
            accepter.start();

            PricingApiClient rudeClient = clientFor("http://"
                    + InetAddress.getLoopbackAddress().getHostAddress() + ":" + rudeServer.getLocalPort());

            assertThatThrownBy(() -> rudeClient.fetchQuote("SKU-1001", "DE", "EUR"))
                    .isInstanceOfSatisfying(PricingException.class,
                            ex -> assertThat(ex.reason()).isEqualTo(PricingFailureReason.TRANSPORT_ERROR));
        }
    }
}
