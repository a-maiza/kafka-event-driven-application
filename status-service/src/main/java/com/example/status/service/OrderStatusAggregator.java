package com.example.status.service;

import com.example.common.avro.OrderStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderStatusAggregator {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusAggregator.class);

    private final ConcurrentHashMap<String, OrderAggregation> aggregations = new ConcurrentHashMap<>();

    public OrderStatusChanged handlePaymentOutcome(String orderId, String paymentStatus, String correlationId) {
        synchronized (orderId.intern()) {
            OrderAggregation agg = aggregations.computeIfAbsent(orderId, k -> new OrderAggregation());
            agg.setPaymentStatus(paymentStatus);
            agg.setCorrelationId(correlationId);

            log.info("Payment outcome for order {}: {}", orderId, paymentStatus);

            if (agg.isComplete()) {
                return buildAndCleanup(orderId, agg);
            }
            return null;
        }
    }

    public OrderStatusChanged handleInventoryOutcome(String orderId, String inventoryStatus, String correlationId) {
        synchronized (orderId.intern()) {
            OrderAggregation agg = aggregations.computeIfAbsent(orderId, k -> new OrderAggregation());
            agg.setInventoryStatus(inventoryStatus);
            agg.setCorrelationId(correlationId);

            log.info("Inventory outcome for order {}: {}", orderId, inventoryStatus);

            if (agg.isComplete()) {
                return buildAndCleanup(orderId, agg);
            }
            return null;
        }
    }

    private OrderStatusChanged buildAndCleanup(String orderId, OrderAggregation agg) {
        String finalStatus;
        if ("AUTHORIZED".equals(agg.getPaymentStatus()) && "RESERVED".equals(agg.getInventoryStatus())) {
            finalStatus = "CONFIRMED";
        } else {
            finalStatus = "REJECTED";
        }

        log.info("Order {} aggregation complete: payment={}, inventory={}, final={}",
                orderId, agg.getPaymentStatus(), agg.getInventoryStatus(), finalStatus);

        aggregations.remove(orderId);

        return OrderStatusChanged.newBuilder()
                .setOrderId(orderId)
                .setPaymentStatus(agg.getPaymentStatus())
                .setInventoryStatus(agg.getInventoryStatus())
                .setFinalStatus(finalStatus)
                .setUpdatedAt(Instant.now().toString())
                .build();
    }
}
