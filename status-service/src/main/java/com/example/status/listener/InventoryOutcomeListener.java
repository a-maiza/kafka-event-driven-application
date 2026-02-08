package com.example.status.listener;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderStatusChanged;
import com.example.common.avro.StockRejected;
import com.example.common.avro.StockReserved;
import com.example.status.service.IdempotencyCache;
import com.example.status.service.OrderStatusAggregator;
import com.example.status.service.StatusEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryOutcomeListener.class);

    private final OrderStatusAggregator aggregator;
    private final StatusEventPublisher eventPublisher;
    private final IdempotencyCache idempotencyCache;

    public InventoryOutcomeListener(OrderStatusAggregator aggregator,
                                    StatusEventPublisher eventPublisher,
                                    IdempotencyCache idempotencyCache) {
        this.aggregator = aggregator;
        this.eventPublisher = eventPublisher;
        this.idempotencyCache = idempotencyCache;
    }

    @KafkaListener(topics = TopicNames.INVENTORY, groupId = "status-service")
    public void onInventoryOutcome(ConsumerRecord<String, Object> record) {
        String orderId = record.key();
        Object value = record.value();

        try {
            String correlationId = CorrelationIdUtils.getFromHeaders(record);
            CorrelationIdUtils.setInMdc(correlationId);

            String eventId = orderId + "-inventory";
            if (idempotencyCache.contains(eventId)) {
                log.info("Skipping duplicate inventory event for order {}", orderId);
                return;
            }

            String inventoryStatus;
            if (value instanceof StockReserved) {
                inventoryStatus = "RESERVED";
                log.info("Received StockReserved for order {}", orderId);
            } else if (value instanceof StockRejected) {
                inventoryStatus = "REJECTED";
                log.info("Received StockRejected for order {}", orderId);
            } else {
                log.warn("Unknown event type on inventory topic: {}", value.getClass().getName());
                return;
            }

            OrderStatusChanged result = aggregator.handleInventoryOutcome(orderId, inventoryStatus, correlationId);
            if (result != null) {
                eventPublisher.publish(orderId, result);
            }

            idempotencyCache.mark(eventId);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
