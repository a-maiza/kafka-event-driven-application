package com.example.order.controller;

import com.example.order.model.Order;
import com.example.order.model.OrderLineItem;
import com.example.order.model.OrderStatus;
import com.example.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void postOrders_shouldReturn201WithOrderIdAndStatus() throws Exception {
        Order order = Order.builder()
                .id("order-123")
                .customerId("cust-1")
                .lines(List.of(new OrderLineItem("SKU-001", 2)))
                .total(new BigDecimal("99.99"))
                .status(OrderStatus.CREATED)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        when(orderService.createOrder(any())).thenReturn(order);

        String body = """
                {"customerId":"cust-1","lines":[{"sku":"SKU-001","qty":2}],"total":99.99}
                """;

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getOrder_shouldReturn200WithFullDto() throws Exception {
        Order order = Order.builder()
                .id("order-123")
                .customerId("cust-1")
                .lines(List.of(new OrderLineItem("SKU-001", 2)))
                .total(new BigDecimal("99.99"))
                .status(OrderStatus.CREATED)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        when(orderService.getOrder("order-123")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/orders/order-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-123"))
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andExpect(jsonPath("$.lines[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.lines[0].qty").value(2))
                .andExpect(jsonPath("$.total").value(99.99))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getOrder_shouldReturn404WhenNotFound() throws Exception {
        when(orderService.getOrder("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/unknown"))
                .andExpect(status().isNotFound());
    }
}
