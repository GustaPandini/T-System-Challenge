package com.tsystems.challenge.orders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubbedPricingConfig {

    @Bean
    @Primary
    ControllablePricingClient controllablePricingClient() {
        return new ControllablePricingClient();
    }
}
