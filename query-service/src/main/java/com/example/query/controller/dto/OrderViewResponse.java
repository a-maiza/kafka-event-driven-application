package com.example.query.controller.dto;

import java.util.List;

public record OrderViewResponse(
        String id,
        String customerId,
        List<OrderLineDto> lines,
        String total,
        String status,
        String createdAt,
        String paymentStatus,
        String inventoryStatus,
        String finalStatus
) {}
