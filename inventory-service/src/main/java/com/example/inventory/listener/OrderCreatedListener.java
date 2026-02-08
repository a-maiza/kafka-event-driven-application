package com.example.inventory.listener;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.inventory.service.IdempotencyCache;
import com.example.inventory.service.InventoryEventPublisher;
import com.example.inventory.service.StockReservationService;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final StockReservationService reservationService;
    private final InventoryEventPublisher eventPublisher;
    private final IdempotencyCache idempotencyCache;

    public OrderCreatedListener(StockReservationService reservationService,
                                InventoryEventPublisher eventPublisher,
                                IdempotencyCache idempotencyCache) {
        this.reservationService = reservationService;
        this.eventPublisher = eventPublisher;
        this.idempotencyCache = idempotencyCache;
    }

    @KafkaListener(topics = TopicNames.ORDERS, groupId = "inventory-service")
    public void onOrderCreated(ConsumerRecord<String, OrderCreated> record) {
        OrderCreated event = record.value();
        String eventId = event.getId();

        try {
            String correlationId = CorrelationIdUtils.getFromHeaders(record);
            CorrelationIdUtils.setInMdc(correlationId);

            if (idempotencyCache.contains(eventId)) {
                log.info("Skipping duplicate event: {}", eventId);
                return;
            }

            log.info("Processing OrderCreated event for order {}", eventId);

            SpecificRecordBase result = reservationService.reserve(event);
            eventPublisher.publish(eventId, result);

            idempotencyCache.mark(eventId);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
