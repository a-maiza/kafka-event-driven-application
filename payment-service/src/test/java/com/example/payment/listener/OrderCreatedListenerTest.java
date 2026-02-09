package com.example.payment.listener;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.common.avro.PaymentAuthorized;
import com.example.payment.service.IdempotencyCache;
import com.example.payment.service.PaymentAuthorizationService;
import com.example.payment.service.PaymentEventPublisher;
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
class OrderCreatedListenerTest {

    @Mock
    private PaymentAuthorizationService authorizationService;
    @Mock
    private PaymentEventPublisher eventPublisher;
    @Mock
    private IdempotencyCache idempotencyCache;

    private OrderCreatedListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderCreatedListener(authorizationService, eventPublisher, idempotencyCache);
    }

    private ConsumerRecord<String, OrderCreated> buildRecord(String orderId) {
        OrderCreated event = OrderCreated.newBuilder()
                .setId(orderId)
                .setCustomerId("cust-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setTotal("100")
                .setStatus("CREATED")
                .setCreatedAt("2025-01-01T00:00:00Z")
                .build();

        RecordHeaders headers = new RecordHeaders();
        headers.add("correlationId", "corr-123".getBytes(StandardCharsets.UTF_8));
        return new ConsumerRecord<>("orders.v1", 0, 0, 0L,
                null, 0, 0, orderId, event, headers, null);
    }

    @Test
    void onOrderCreated_shouldAuthorizeAndPublish() {
        ConsumerRecord<String, OrderCreated> record = buildRecord("order-1");
        PaymentAuthorized result = PaymentAuthorized.newBuilder()
                .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build();

        when(idempotencyCache.contains("order-1")).thenReturn(false);
        when(authorizationService.authorize(any())).thenReturn(result);

        listener.onOrderCreated(record);

        verify(authorizationService).authorize(any(OrderCreated.class));
        verify(eventPublisher).publish(eq("order-1"), eq(result));
        verify(idempotencyCache).mark("order-1");
    }

    @Test
    void onOrderCreated_duplicate_shouldSkip() {
        ConsumerRecord<String, OrderCreated> record = buildRecord("order-1");
        when(idempotencyCache.contains("order-1")).thenReturn(true);

        listener.onOrderCreated(record);

        verify(authorizationService, never()).authorize(any());
        verify(eventPublisher, never()).publish(any(), any());
    }

    @Test
    void onOrderCreated_shouldClearMdc() {
        ConsumerRecord<String, OrderCreated> record = buildRecord("order-1");
        when(idempotencyCache.contains("order-1")).thenReturn(false);
        when(authorizationService.authorize(any())).thenReturn(
                PaymentAuthorized.newBuilder()
                        .setOrderId("order-1").setAmount("100").setAuthorizedAt("now").build());

        listener.onOrderCreated(record);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
