package com.example.order.service;

import com.example.order.controller.dto.CreateOrderRequest;
import com.example.order.model.Order;
import com.example.order.model.OrderLineItem;
import com.example.order.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public Order createOrder(CreateOrderRequest request) {
        String orderId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        var lines = request.lines().stream()
                .map(l -> new OrderLineItem(l.sku(), l.qty()))
                .toList();

        Order order = Order.builder()
                .id(orderId)
                .customerId(request.customerId())
                .lines(lines)
                .total(request.total())
                .status(OrderStatus.CREATED)
                .createdAt(now)
                .build();

        orderStore.put(orderId, order);
        log.info("Order created: {}", orderId);

        eventPublisher.publishOrderCreated(order);

        return order;
    }

    public Optional<Order> getOrder(String id) {
        return Optional.ofNullable(orderStore.get(id));
    }
}
