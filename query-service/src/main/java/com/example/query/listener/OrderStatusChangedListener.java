package com.example.query.listener;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderStatusChanged;
import com.example.query.service.IdempotencyCache;
import com.example.query.service.OrderViewStore;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusChangedListener.class);

    private final OrderViewStore orderViewStore;
    private final IdempotencyCache idempotencyCache;

    public OrderStatusChangedListener(OrderViewStore orderViewStore,
                                      IdempotencyCache idempotencyCache) {
        this.orderViewStore = orderViewStore;
        this.idempotencyCache = idempotencyCache;
    }

    @KafkaListener(topics = TopicNames.ORDER_STATUS, groupId = "query-service")
    public void onOrderStatusChanged(ConsumerRecord<String, OrderStatusChanged> record) {
        OrderStatusChanged event = record.value();
        String eventId = event.getOrderId() + "-status";

        try {
            String correlationId = CorrelationIdUtils.getFromHeaders(record);
            CorrelationIdUtils.setInMdc(correlationId);

            if (idempotencyCache.contains(eventId)) {
                log.info("Skipping duplicate OrderStatusChanged event for order {}", event.getOrderId());
                return;
            }

            log.info("Updating materialized view for order {} with status {}",
                    event.getOrderId(), event.getFinalStatus());
            orderViewStore.updateFromStatusChanged(event);

            idempotencyCache.mark(eventId);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
