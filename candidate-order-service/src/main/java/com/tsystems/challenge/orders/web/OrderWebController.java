package com.tsystems.challenge.orders.web;

import com.tsystems.challenge.orders.domain.Order;
import com.tsystems.challenge.orders.domain.OrderStatus;
import com.tsystems.challenge.orders.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Controller
public class OrderWebController {
    private final OrderService orderService;

    public OrderWebController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(name = "created", required = false) UUID createdOrderId,
            @RequestParam(name = "retried", required = false) UUID retriedOrderId,
            @RequestParam(name = "retriedAll", required = false) Integer retriedAllCount,
            Model model
    ) {
        if (!model.containsAttribute("orderForm")) {
            model.addAttribute("orderForm", new CreateOrderForm());
        }
        model.addAttribute("createdOrder", createdOrderId == null ? null : orderService.get(createdOrderId));
        model.addAttribute("retriedOrder", retriedOrderId == null ? null : orderService.get(retriedOrderId));
        model.addAttribute("retriedAllCount", retriedAllCount);
        addDashboardData(model);
        return "orders";
    }

    @PostMapping("/ui/orders")
    public String create(
            @Valid @ModelAttribute("orderForm") CreateOrderForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            addDashboardData(model);
            return "orders";
        }

        try {
            Order order = orderService.create(form.toRequest());
            redirectAttributes.addAttribute("created", order.id());
            return "redirect:/";
        } catch (RuntimeException ex) {
            model.addAttribute("integrationError", "Unexpected internal error. The order was not stored.");
            addDashboardData(model);
            return "orders";
        }
    }

    @PostMapping("/ui/orders/{id}/retry")
    public String retryPricing(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        Order order = orderService.retryPricing(id);
        redirectAttributes.addAttribute("retried", order.id());
        return "redirect:/";
    }

    @PostMapping("/ui/orders/retry-pending")
    public String retryAllPending(RedirectAttributes redirectAttributes) {
        List<Order> retried = orderService.retryAllPending();
        redirectAttributes.addAttribute("retriedAll", retried.size());
        return "redirect:/";
    }

    private void addDashboardData(Model model) {
        List<Order> orders = orderService.list().stream()
                .sorted(Comparator.comparing(Order::createdAt).reversed())
                .toList();

        long priced = countOf(orders, OrderStatus.CONFIRMED);
        long awaiting = countOf(orders, OrderStatus.PENDING_PRICE);
        long attention = countOf(orders, OrderStatus.PRICING_FAILED);

        model.addAttribute("orders", orders);
        model.addAttribute("orderCount", orders.size());
        model.addAttribute("pricedCount", priced);
        model.addAttribute("awaitingCount", awaiting);
        model.addAttribute("attentionCount", attention);
        model.addAttribute("hasPending", awaiting > 0);
    }

    private static long countOf(List<Order> orders, OrderStatus status) {
        return orders.stream().filter(order -> order.status() == status).count();
    }
}
