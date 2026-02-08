package com.example.status.service;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatusEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StatusEventPublisher.class);

    private final KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    public StatusEventPublisher(KafkaTemplate<String, SpecificRecordBase> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String orderId, SpecificRecordBase event) {
        ProducerRecord<String, SpecificRecordBase> record =
                new ProducerRecord<>(TopicNames.ORDER_STATUS, orderId, event);

        String correlationId = CorrelationIdUtils.getFromMdc();
        CorrelationIdUtils.setToHeaders(record, correlationId);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderStatusChanged for order {}: {}",
                                orderId, ex.getMessage(), ex);
                    } else {
                        log.info("Published OrderStatusChanged for order {} to partition {} offset {}",
                                orderId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
