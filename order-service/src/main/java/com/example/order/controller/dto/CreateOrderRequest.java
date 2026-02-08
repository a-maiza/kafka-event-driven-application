package com.example.order.controller.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        String customerId,
        List<OrderLineDto> lines,
        BigDecimal total
) {
}
