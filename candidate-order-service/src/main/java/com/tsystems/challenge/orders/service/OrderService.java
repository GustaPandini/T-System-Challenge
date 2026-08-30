package com.tsystems.challenge.orders.service;

import com.tsystems.challenge.orders.domain.Order;
import com.tsystems.challenge.orders.domain.OrderStatus;
import com.tsystems.challenge.orders.domain.PricingFailure;
import com.tsystems.challenge.orders.dto.CreateOrderRequest;
import com.tsystems.challenge.orders.dto.PriceQuote;
import com.tsystems.challenge.orders.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final PricingClient pricingClient;
    private final Clock clock;

    @Autowired
    public OrderService(OrderRepository orderRepository, PricingClient pricingClient) {
        this(orderRepository, pricingClient, Clock.systemUTC());
    }

    OrderService(OrderRepository orderRepository, PricingClient pricingClient, Clock clock) {
        this.orderRepository = orderRepository;
        this.pricingClient = pricingClient;
        this.clock = clock;
    }

    public Order create(CreateOrderRequest request) {
        Order accepted = orderRepository.save(new Order(
                UUID.randomUUID(),
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.country(),
                request.currency(),
                null,
                null,
                OrderStatus.PENDING_PRICE,
                Instant.now(clock),
                null
        ));

        return attemptPricing(accepted);
    }

    public Order retryPricing(UUID id) {
        Order order = get(id);

        if (order.status() != OrderStatus.PENDING_PRICE) {
            return order;
        }

        return attemptPricing(order);
    }

    public List<Order> retryAllPending() {
        return orderRepository.findAll().stream()
                .filter(order -> order.status() == OrderStatus.PENDING_PRICE)
                .map(this::attemptPricing)
                .toList();
    }

    private Order attemptPricing(Order order) {
        try {
            PriceQuote quote = pricingClient.fetchQuote(order.productId(), order.country(), order.currency());
            return orderRepository.save(order.priced(quote.amount()));
        } catch (PricingException ex) {
            PricingFailure failure = ex.toFailure(attemptNumber(order), Instant.now(clock));

            log.warn("Pricing failed for order {}: attempt={} reason={} requestId={} detail={}",
                    order.id(), failure.attempts(), failure.reason(),
                    failure.providerRequestId(), failure.detail());

            return orderRepository.save(ex.isRetryable()
                    ? order.awaitingPrice(failure)
                    : order.pricingFailed(failure));
        }
    }

    private static int attemptNumber(Order order) {
        return order.pricingFailure() == null ? 1 : order.pricingFailure().attempts() + 1;
    }

    public Order get(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }
}
