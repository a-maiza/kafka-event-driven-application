package com.example.order.service;

import com.example.order.controller.dto.CreateOrderRequest;
import com.example.order.controller.dto.OrderLineDto;
import com.example.order.model.Order;
import com.example.order.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderEventPublisher eventPublisher;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(eventPublisher);
    }

    @Test
    void createOrder_shouldGenerateIdAndPersist() {
        var request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderLineDto("SKU-001", 2)),
                new BigDecimal("99.99"));

        Order order = orderService.createOrder(request);

        assertThat(order.getId()).isNotNull().isNotEmpty();
        assertThat(order.getCustomerId()).isEqualTo("customer-1");
        assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("99.99"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getLines()).hasSize(1);
        assertThat(order.getLines().getFirst().sku()).isEqualTo("SKU-001");
        assertThat(order.getLines().getFirst().qty()).isEqualTo(2);
    }

    @Test
    void createOrder_shouldPublishEvent() {
        var request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderLineDto("SKU-001", 1)),
                new BigDecimal("50"));

        orderService.createOrder(request);

        verify(eventPublisher).publishOrderCreated(any(Order.class));
    }

    @Test
    void createOrder_withMultipleLines_shouldMapAllLines() {
        var request = new CreateOrderRequest(
                "customer-2",
                List.of(new OrderLineDto("SKU-001", 1), new OrderLineDto("SKU-002", 3)),
                new BigDecimal("200"));

        Order order = orderService.createOrder(request);

        assertThat(order.getLines()).hasSize(2);
    }

    @Test
    void getOrder_shouldReturnExistingOrder() {
        var request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderLineDto("SKU-001", 1)),
                new BigDecimal("50"));

        Order created = orderService.createOrder(request);
        Optional<Order> found = orderService.getOrder(created.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(created.getId());
    }

    @Test
    void getOrder_shouldReturnEmptyForUnknownId() {
        Optional<Order> found = orderService.getOrder("non-existent-id");

        assertThat(found).isEmpty();
    }
}
