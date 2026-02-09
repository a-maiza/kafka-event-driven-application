package com.example.status.listener;

import com.example.common.avro.OrderStatusChanged;
import com.example.common.avro.StockRejected;
import com.example.common.avro.StockReserved;
import com.example.common.avro.OrderLine;
import com.example.status.service.IdempotencyCache;
import com.example.status.service.OrderStatusAggregator;
import com.example.status.service.StatusEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryOutcomeListenerTest {

    @Mock
    private OrderStatusAggregator aggregator;
    @Mock
    private StatusEventPublisher eventPublisher;
    @Mock
    private IdempotencyCache idempotencyCache;

    private InventoryOutcomeListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryOutcomeListener(aggregator, eventPublisher, idempotencyCache);
    }

    private ConsumerRecord<String, Object> buildRecord(String orderId, Object value) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("correlationId", "corr-123".getBytes(StandardCharsets.UTF_8));
        return new ConsumerRecord<>("inventory.v1", 0, 0, 0L,
                null, 0, 0, orderId, value, headers, null);
    }

    @Test
    void onStockReserved_shouldAggregateWithReservedStatus() {
        StockReserved event = StockReserved.newBuilder()
                .setOrderId("order-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setReservedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-inventory")).thenReturn(false);

        listener.onInventoryOutcome(record);

        verify(aggregator).handleInventoryOutcome(eq("order-1"), eq("RESERVED"), any());
        verify(idempotencyCache).mark("order-1-inventory");
    }

    @Test
    void onStockRejected_shouldAggregateWithRejectedStatus() {
        StockRejected event = StockRejected.newBuilder()
                .setOrderId("order-1").setReason("Out of stock").setRejectedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-inventory")).thenReturn(false);

        listener.onInventoryOutcome(record);

        verify(aggregator).handleInventoryOutcome(eq("order-1"), eq("REJECTED"), any());
    }

    @Test
    void onInventoryOutcome_whenAggregationComplete_shouldPublish() {
        StockReserved event = StockReserved.newBuilder()
                .setOrderId("order-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setReservedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);

        OrderStatusChanged statusChanged = OrderStatusChanged.newBuilder()
                .setOrderId("order-1").setPaymentStatus("AUTHORIZED")
                .setInventoryStatus("RESERVED").setFinalStatus("CONFIRMED")
                .setUpdatedAt("now").build();

        when(idempotencyCache.contains("order-1-inventory")).thenReturn(false);
        when(aggregator.handleInventoryOutcome(any(), any(), any())).thenReturn(statusChanged);

        listener.onInventoryOutcome(record);

        verify(eventPublisher).publish(eq("order-1"), eq(statusChanged));
    }

    @Test
    void onInventoryOutcome_whenAggregationNotComplete_shouldNotPublish() {
        StockReserved event = StockReserved.newBuilder()
                .setOrderId("order-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setReservedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);

        when(idempotencyCache.contains("order-1-inventory")).thenReturn(false);
        when(aggregator.handleInventoryOutcome(any(), any(), any())).thenReturn(null);

        listener.onInventoryOutcome(record);

        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void onInventoryOutcome_duplicate_shouldSkip() {
        StockReserved event = StockReserved.newBuilder()
                .setOrderId("order-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setReservedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-inventory")).thenReturn(true);

        listener.onInventoryOutcome(record);

        verify(aggregator, never()).handleInventoryOutcome(any(), any(), any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void onInventoryOutcome_shouldClearMdc() {
        StockReserved event = StockReserved.newBuilder()
                .setOrderId("order-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setReservedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-inventory")).thenReturn(false);
        when(aggregator.handleInventoryOutcome(any(), any(), any())).thenReturn(null);

        listener.onInventoryOutcome(record);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
