package com.example.order.service;

import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.order.model.Order;
import com.example.order.model.OrderLineItem;
import com.example.order.model.OrderStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, OrderCreated> kafkaTemplate;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, OrderCreated>> recordCaptor;

    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishOrderCreated_shouldSendToCorrectTopicWithOrderIdAsKey() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(new CompletableFuture<>());

        Order order = Order.builder()
                .id("order-123")
                .customerId("cust-1")
                .lines(List.of(new OrderLineItem("SKU-001", 2)))
                .total(new BigDecimal("99.99"))
                .status(OrderStatus.CREATED)
                .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();

        publisher.publishOrderCreated(order);

        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, OrderCreated> record = recordCaptor.getValue();

        assertThat(record.topic()).isEqualTo(TopicNames.ORDERS);
        assertThat(record.key()).isEqualTo("order-123");

        OrderCreated event = record.value();
        assertThat(event.getId()).isEqualTo("order-123");
        assertThat(event.getCustomerId()).isEqualTo("cust-1");
        assertThat(event.getTotal()).isEqualTo("99.99");
        assertThat(event.getStatus()).isEqualTo("CREATED");
        assertThat(event.getLines()).hasSize(1);
        assertThat(event.getLines().getFirst().getSku()).isEqualTo("SKU-001");
        assertThat(event.getLines().getFirst().getQty()).isEqualTo(2);
    }
}
