package com.tsystems.challenge.orders;

import com.tsystems.challenge.orders.domain.Order;
import com.tsystems.challenge.orders.domain.OrderStatus;
import com.tsystems.challenge.orders.domain.PricingFailureReason;
import com.tsystems.challenge.orders.dto.CreateOrderRequest;
import com.tsystems.challenge.orders.repository.InMemoryOrderRepository;
import com.tsystems.challenge.orders.service.OrderNotFoundException;
import com.tsystems.challenge.orders.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest {

    private ControllablePricingClient provider;
    private OrderService service;

    @BeforeEach
    void setUp() {
        provider = new ControllablePricingClient();
        service = new OrderService(new InMemoryOrderRepository(), provider);
    }

    private Order anOrder() {
        return service.create(new CreateOrderRequest("customer-42", "SKU-1001", 2, "DE", "EUR"));
    }

    @Test
    void confirmsTheOrderWhenTheProviderReturnsAUsableQuote() {
        Order order = anOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.unitPrice()).isEqualByComparingTo("19.99");
        assertThat(order.totalPrice()).isEqualByComparingTo("39.98");
        assertThat(order.pricingFailure()).isNull();
    }

    @Test
    void keepsTheOrderWithAStableIdWhenTheProviderIsDown() {
        provider.failTemporarily();

        Order order = anOrder();

        assertThat(order.id()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PRICE);
        assertThat(order.unitPrice()).isNull();
        assertThat(service.get(order.id())).isEqualTo(order);
    }

    @Test
    void recordsWhyTheOrderCouldNotBePriced() {
        provider.failTemporarily();

        Order order = anOrder();

        assertThat(order.pricingFailure()).isNotNull();
        assertThat(order.pricingFailure().reason()).isEqualTo(PricingFailureReason.PROVIDER_UNAVAILABLE);
        assertThat(order.pricingFailure().providerRequestId()).isEqualTo("req-test-transient");
        assertThat(order.pricingFailure().attempts()).isEqualTo(1);
        assertThat(order.pricingFailure().lastAttemptAt()).isNotNull();
    }

    @Test
    void marksTheOrderAsFailedWhenTheProviderRefusesPermanently() {
        provider.failPermanently();

        Order order = anOrder();

        assertThat(order.status()).isEqualTo(OrderStatus.PRICING_FAILED);
        assertThat(order.pricingFailure().reason()).isEqualTo(PricingFailureReason.PRODUCT_NOT_FOUND);
        assertThat(order.pricingFailure().reason().isRetryable()).isFalse();
    }

    @Test
    void confirmsAPendingOrderOnceTheProviderRecovers() {
        provider.failTemporarily();
        Order pending = anOrder();

        provider.behaveNormally();
        Order recovered = service.retryPricing(pending.id());

        assertThat(recovered.id()).isEqualTo(pending.id());
        assertThat(recovered.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(recovered.unitPrice()).isEqualByComparingTo("19.99");
        assertThat(recovered.pricingFailure()).isNull();
    }

    @Test
    void accumulatesTheAttemptCountAcrossRetries() {
        provider.failTemporarily();
        Order order = anOrder();

        service.retryPricing(order.id());
        Order afterThird = service.retryPricing(order.id());

        assertThat(afterThird.pricingFailure().attempts()).isEqualTo(3);
    }

    @Test
    void doesNotRepriceAnOrderThatIsAlreadyConfirmed() {
        Order confirmed = anOrder();
        provider.resetCalls();

        Order afterRetry = service.retryPricing(confirmed.id());

        assertThat(provider.calls()).isZero();
        assertThat(afterRetry).isEqualTo(confirmed);
    }

    @Test
    void doesNotRetryAnOrderThatFailedPermanently() {
        provider.failPermanently();
        Order failed = anOrder();
        provider.resetCalls();

        Order afterRetry = service.retryPricing(failed.id());

        assertThat(provider.calls()).isZero();
        assertThat(afterRetry.status()).isEqualTo(OrderStatus.PRICING_FAILED);
    }

    @Test
    void retriesEveryPendingOrderAndLeavesTheOthersAlone() {
        provider.behaveNormally();
        Order alreadyPriced = anOrder();

        provider.failTemporarily();
        anOrder();
        anOrder();

        provider.behaveNormally();
        provider.resetCalls();
        var retried = service.retryAllPending();

        assertThat(retried).hasSize(2);
        assertThat(provider.calls()).isEqualTo(2);
        assertThat(retried).allMatch(order -> order.status() == OrderStatus.CONFIRMED);
        assertThat(service.get(alreadyPriced.id())).isEqualTo(alreadyPriced);
    }

    @Test
    void failsWhenTheOrderDoesNotExist() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID()))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
