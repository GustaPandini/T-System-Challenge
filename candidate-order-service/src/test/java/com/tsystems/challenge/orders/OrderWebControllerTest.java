package com.tsystems.challenge.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class OrderWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersTheOrderDashboard() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("orders"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("International Order Service")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Create an order")));
    }

    @Test
    void createsAnOrderFromTheHtmlForm() throws Exception {
        mockMvc.perform(post("/ui/orders")
                        .param("customerId", "customer-web")
                        .param("productId", "SKU-1001")
                        .param("quantity", "2")
                        .param("country", "DE")
                        .param("currency", "EUR"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/?created=")));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid")));
    }
}
