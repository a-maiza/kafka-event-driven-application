package com.example.query.listener;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.query.service.IdempotencyCache;
import com.example.query.service.OrderViewStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final OrderViewStore orderViewStore;
    private final IdempotencyCache idempotencyCache;

    public OrderCreatedListener(OrderViewStore orderViewStore,
                                IdempotencyCache idempotencyCache) {
        this.orderViewStore = orderViewStore;
        this.idempotencyCache = idempotencyCache;
    }

    @KafkaListener(topics = TopicNames.ORDERS, groupId = "query-service")
    public void onOrderCreated(ConsumerRecord<String, OrderCreated> record) {
        OrderCreated event = record.value();
        String eventId = event.getId() + "-order";

        try {
            String correlationId = CorrelationIdUtils.getFromHeaders(record);
            CorrelationIdUtils.setInMdc(correlationId);

            if (idempotencyCache.contains(eventId)) {
                log.info("Skipping duplicate OrderCreated event for order {}", event.getId());
                return;
            }

            log.info("Materializing OrderCreated for order {}", event.getId());
            orderViewStore.createFromOrderCreated(event);

            idempotencyCache.mark(eventId);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
