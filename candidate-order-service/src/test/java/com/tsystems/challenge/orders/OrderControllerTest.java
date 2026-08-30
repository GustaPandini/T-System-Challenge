package com.tsystems.challenge.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StubbedPricingConfig.class)
class OrderControllerTest {

    private static final String BODY = """
            {"customerId":"%s","productId":"SKU-1001","quantity":2,"country":"DE","currency":"EUR"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ControllablePricingClient provider;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void startWithAHealthyProvider() {
        provider.behaveNormally();
    }

    private JsonNode createOrder(String customerId) throws Exception {
        String json = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted(customerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void createReturns201WithTheLocationOfTheNewOrder() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("customer-api")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/orders/")))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.unitPrice").value(19.99))
                .andExpect(jsonPath("$.totalPrice").value(39.98));
    }

    @Test
    void createStillReturns201DuringAnOutageButTheBodySaysItIsNotPriced() throws Exception {
        provider.failTemporarily();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.formatted("customer-api-outage")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("PENDING_PRICE"))
                .andExpect(jsonPath("$.unitPrice").doesNotExist())
                .andExpect(jsonPath("$.pricingFailure.reason").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.pricingFailure.providerRequestId").value("req-test-transient"))
                .andExpect(jsonPath("$.pricingFailure.attempts").value(1));
    }

    @Test
    void retryConfirmsThePendingOrderOnceTheProviderRecovers() throws Exception {
        provider.failTemporarily();
        String id = createOrder("customer-api-retry").get("id").asText();

        provider.behaveNormally();
        mockMvc.perform(post("/api/orders/" + id + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.pricingFailure").doesNotExist());
    }

    @Test
    void retryIsANoOpOnAnOrderThatIsAlreadyConfirmed() throws Exception {
        String id = createOrder("customer-api-noop").get("id").asText();
        provider.resetCalls();

        mockMvc.perform(post("/api/orders/" + id + "/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(provider.calls()).isZero();
    }

    @Test
    void getReturns404ForAnUnknownOrder() throws Exception {
        mockMvc.perform(get("/api/orders/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReturns400ForAMalformedOrderId() throws Exception {
        mockMvc.perform(get("/api/orders/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
