package com.tsystems.challenge.orders.controller;

import com.tsystems.challenge.orders.domain.Order;
import com.tsystems.challenge.orders.dto.CreateOrderRequest;
import com.tsystems.challenge.orders.dto.OrderResponse;
import com.tsystems.challenge.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity
                .created(URI.create("/api/orders/" + order.id()))
                .body(OrderResponse.from(order));
    }

    @PostMapping("/{id}/retry")
    public OrderResponse retryPricing(@PathVariable UUID id) {
        return OrderResponse.from(orderService.retryPricing(id));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.get(id));
    }

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.list().stream().map(OrderResponse::from).toList();
    }
}
