package com.example.inventory.service;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, SpecificRecordBase> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String orderId, SpecificRecordBase event) {
        ProducerRecord<String, SpecificRecordBase> record =
                new ProducerRecord<>(TopicNames.INVENTORY, orderId, event);

        String correlationId = CorrelationIdUtils.getFromMdc();
        CorrelationIdUtils.setToHeaders(record, correlationId);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}: {}",
                                event.getClass().getSimpleName(), orderId, ex.getMessage(), ex);
                    } else {
                        log.info("Published {} for order {} to partition {} offset {}",
                                event.getClass().getSimpleName(), orderId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
