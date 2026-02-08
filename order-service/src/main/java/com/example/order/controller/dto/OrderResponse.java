package com.example.order.controller.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        List<OrderLineDto> lines,
        BigDecimal total,
        String status,
        String createdAt
) {
}
