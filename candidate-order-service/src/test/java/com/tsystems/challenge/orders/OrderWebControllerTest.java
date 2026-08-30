package com.tsystems.challenge.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Import(StubbedPricingConfig.class)
class OrderWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ControllablePricingClient provider;

    @BeforeEach
    void startWithAHealthyProvider() {
        provider.behaveNormally();
    }

    private MockHttpServletRequestBuilder anOrderFor(String customerId) {
        return post("/ui/orders")
                .param("customerId", customerId)
                .param("productId", "SKU-1001")
                .param("quantity", "2")
                .param("country", "DE")
                .param("currency", "EUR");
    }

    private String createOrderAndReturnId(String customerId) throws Exception {
        String location = mockMvc.perform(anOrderFor(customerId))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.indexOf('=') + 1);
    }

    @Test
    void rendersTheOrderDashboard() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(content().string(containsString("International Order Service")))
                .andExpect(content().string(containsString("Create an order")));
    }

    @Test
    void createsAnOrderFromTheHtmlForm() throws Exception {
        mockMvc.perform(anOrderFor("customer-web"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/?created=")));
    }

    @Test
    void showsValidationErrorsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/ui/orders")
                        .param("customerId", "")
                        .param("productId", "SKU-1001")
                        .param("quantity", "0")
                        .param("country", "de")
                        .param("currency", "eur"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(model().attributeHasFieldErrors("orderForm",
                        "customerId", "quantity", "country", "currency"))
                .andExpect(content().string(containsString("class=\"field-error\"")));
    }

    @Test
    void showsAPricedOrderAsConfirmed() throws Exception {
        String id = createOrderAndReturnId("customer-priced");

        mockMvc.perform(get("/").param("created", id))
                .andExpect(content().string(containsString("Order confirmed and priced.")))
                .andExpect(content().string(containsString("Priced")));
    }

    @Test
    void showsAnOutageAsAcceptedButNotPricedAndTellsTheUserNotToResubmit() throws Exception {
        provider.failTemporarily();
        String id = createOrderAndReturnId("customer-outage");

        mockMvc.perform(get("/").param("created", id))
                .andExpect(content().string(containsString("Order accepted, not priced yet.")))
                .andExpect(content().string(containsString("must not be submitted again")))
                .andExpect(content().string(containsString("Awaiting price")))
                .andExpect(content().string(containsString("The pricing provider could not be reached.")))
                .andExpect(content().string(containsString("Retry pricing")));
    }

    @Test
    void showsAPermanentFailureAsNeedingAttentionWithGuidanceForTheStore() throws Exception {
        provider.failPermanently();
        String id = createOrderAndReturnId("customer-permanent");

        mockMvc.perform(get("/").param("created", id))
                .andExpect(content().string(containsString("it cannot be priced")))
                .andExpect(content().string(containsString("Needs attention")))
                .andExpect(content().string(containsString("Check the order data")));
    }

    @Test
    void hidesTheTechnicalDetailBehindADisclosureElement() throws Exception {
        provider.failTemporarily();
        String id = createOrderAndReturnId("customer-detail");

        mockMvc.perform(get("/").param("created", id))
                .andExpect(content().string(containsString("<summary>Technical detail (support)</summary>")))
                .andExpect(content().string(containsString("req-test-transient")))
                .andExpect(content().string(containsString("PROVIDER_UNAVAILABLE")));
    }

    @Test
    void theRetryActionConfirmsTheOrderOnceTheProviderRecovers() throws Exception {
        provider.failTemporarily();
        String id = createOrderAndReturnId("customer-recovered");

        provider.behaveNormally();
        mockMvc.perform(post("/ui/orders/" + id + "/retry"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("retried=" + id)));

        mockMvc.perform(get("/").param("retried", id))
                .andExpect(content().string(containsString("Pricing recovered.")));
    }

    @Test
    void theRetryActionKeepsTheOrderPendingWhenTheProviderIsStillDown() throws Exception {
        provider.failTemporarily();
        String id = createOrderAndReturnId("customer-still-down");

        mockMvc.perform(post("/ui/orders/" + id + "/retry"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/").param("retried", id))
                .andExpect(content().string(containsString("Still not priced.")));
    }

    @Test
    void retriesEveryPendingOrderFromASingleForm() throws Exception {
        provider.failTemporarily();
        createOrderAndReturnId("customer-bulk-1");
        createOrderAndReturnId("customer-bulk-2");

        provider.behaveNormally();
        mockMvc.perform(post("/ui/orders/retry-pending"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("retriedAll=")));
    }
}
