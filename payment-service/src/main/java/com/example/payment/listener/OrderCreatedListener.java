package com.example.payment.listener;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderCreated;
import com.example.payment.service.IdempotencyCache;
import com.example.payment.service.PaymentAuthorizationService;
import com.example.payment.service.PaymentEventPublisher;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final PaymentAuthorizationService authorizationService;
    private final PaymentEventPublisher eventPublisher;
    private final IdempotencyCache idempotencyCache;

    public OrderCreatedListener(PaymentAuthorizationService authorizationService,
                                PaymentEventPublisher eventPublisher,
                                IdempotencyCache idempotencyCache) {
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.idempotencyCache = idempotencyCache;
    }

    @KafkaListener(topics = TopicNames.ORDERS, groupId = "payment-service")
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

            SpecificRecordBase result = authorizationService.authorize(event);
            eventPublisher.publish(eventId, result);

            idempotencyCache.mark(eventId);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
