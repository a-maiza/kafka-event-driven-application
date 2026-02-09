package com.example.status.listener;

import com.example.common.avro.OrderStatusChanged;
import com.example.common.avro.PaymentAuthorized;
import com.example.common.avro.PaymentFailed;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOutcomeListenerTest {

    @Mock
    private OrderStatusAggregator aggregator;
    @Mock
    private StatusEventPublisher eventPublisher;
    @Mock
    private IdempotencyCache idempotencyCache;

    private PaymentOutcomeListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentOutcomeListener(aggregator, eventPublisher, idempotencyCache);
    }

    private ConsumerRecord<String, Object> buildRecord(String orderId, Object value) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("correlationId", "corr-123".getBytes(StandardCharsets.UTF_8));
        return new ConsumerRecord<>("payments.v1", 0, 0, 0L,
                null, 0, 0, orderId, value, headers, null);
    }

    @Test
    void onPaymentAuthorized_shouldAggregateWithAuthorizedStatus() {
        PaymentAuthorized event = PaymentAuthorized.newBuilder()
                .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-payment")).thenReturn(false);

        listener.onPaymentOutcome(record);

        verify(aggregator).handlePaymentOutcome(eq("order-1"), eq("AUTHORIZED"), any());
        verify(idempotencyCache).mark("order-1-payment");
    }

    @Test
    void onPaymentFailed_shouldAggregateWithFailedStatus() {
        PaymentFailed event = PaymentFailed.newBuilder()
                .setOrderId("order-1").setReason("Too high").setFailedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-payment")).thenReturn(false);

        listener.onPaymentOutcome(record);

        verify(aggregator).handlePaymentOutcome(eq("order-1"), eq("FAILED"), any());
    }

    @Test
    void onPaymentOutcome_whenAggregationComplete_shouldPublish() {
        PaymentAuthorized event = PaymentAuthorized.newBuilder()
                .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);

        OrderStatusChanged statusChanged = OrderStatusChanged.newBuilder()
                .setOrderId("order-1").setPaymentStatus("AUTHORIZED")
                .setInventoryStatus("RESERVED").setFinalStatus("CONFIRMED")
                .setUpdatedAt("now").build();

        when(idempotencyCache.contains("order-1-payment")).thenReturn(false);
        when(aggregator.handlePaymentOutcome(any(), any(), any())).thenReturn(statusChanged);

        listener.onPaymentOutcome(record);

        verify(eventPublisher).publish(eq("order-1"), eq(statusChanged));
    }

    @Test
    void onPaymentOutcome_whenAggregationNotComplete_shouldNotPublish() {
        PaymentAuthorized event = PaymentAuthorized.newBuilder()
                .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);

        when(idempotencyCache.contains("order-1-payment")).thenReturn(false);
        when(aggregator.handlePaymentOutcome(any(), any(), any())).thenReturn(null);

        listener.onPaymentOutcome(record);

        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void onPaymentOutcome_duplicate_shouldSkip() {
        PaymentAuthorized event = PaymentAuthorized.newBuilder()
                .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-payment")).thenReturn(true);

        listener.onPaymentOutcome(record);

        verify(aggregator, never()).handlePaymentOutcome(any(), any(), any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void onPaymentOutcome_shouldClearMdc() {
        PaymentAuthorized event = PaymentAuthorized.newBuilder()
                .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build();
        ConsumerRecord<String, Object> record = buildRecord("order-1", event);
        when(idempotencyCache.contains("order-1-payment")).thenReturn(false);
        when(aggregator.handlePaymentOutcome(any(), any(), any())).thenReturn(null);

        listener.onPaymentOutcome(record);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
