package com.example.order.service;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.order.model.Order;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, OrderCreated> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderCreated> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(Order order) {
        OrderCreated event = buildOrderCreatedEvent(order);

        ProducerRecord<String, OrderCreated> record =
                new ProducerRecord<>(TopicNames.ORDERS, order.getId(), event);

        String correlationId = CorrelationIdUtils.getFromMdc();
        CorrelationIdUtils.setToHeaders(record, correlationId);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreated for order {}: {}",
                                order.getId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published OrderCreated for order {} to partition {} offset {}",
                                order.getId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private OrderCreated buildOrderCreatedEvent(Order order) {
        List<OrderLine> avroLines = order.getLines().stream()
                .map(l -> new OrderLine(l.sku(), l.qty()))
                .toList();

        return OrderCreated.newBuilder()
                .setId(order.getId())
                .setCustomerId(order.getCustomerId())
                .setLines(avroLines)
                .setTotal(order.getTotal().toPlainString())
                .setStatus(order.getStatus().name())
                .setCreatedAt(order.getCreatedAt().toString())
                .build();
    }
}
