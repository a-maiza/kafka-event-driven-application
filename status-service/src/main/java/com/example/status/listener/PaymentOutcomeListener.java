package com.example.status.listener;

import com.example.common.CorrelationIdUtils;
import com.example.common.TopicNames;
import com.example.common.avro.OrderStatusChanged;
import com.example.common.avro.PaymentAuthorized;
import com.example.common.avro.PaymentFailed;
import com.example.status.service.IdempotencyCache;
import com.example.status.service.OrderStatusAggregator;
import com.example.status.service.StatusEventPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeListener.class);

    private final OrderStatusAggregator aggregator;
    private final StatusEventPublisher eventPublisher;
    private final IdempotencyCache idempotencyCache;

    public PaymentOutcomeListener(OrderStatusAggregator aggregator,
                                  StatusEventPublisher eventPublisher,
                                  IdempotencyCache idempotencyCache) {
        this.aggregator = aggregator;
        this.eventPublisher = eventPublisher;
        this.idempotencyCache = idempotencyCache;
    }

    @KafkaListener(topics = TopicNames.PAYMENTS, groupId = "status-service")
    public void onPaymentOutcome(ConsumerRecord<String, Object> record) {
        String orderId = record.key();
        Object value = record.value();

        try {
            String correlationId = CorrelationIdUtils.getFromHeaders(record);
            CorrelationIdUtils.setInMdc(correlationId);

            String eventId = orderId + "-payment";
            if (idempotencyCache.contains(eventId)) {
                log.info("Skipping duplicate payment event for order {}", orderId);
                return;
            }

            String paymentStatus;
            if (value instanceof PaymentAuthorized) {
                paymentStatus = "AUTHORIZED";
                log.info("Received PaymentAuthorized for order {}", orderId);
            } else if (value instanceof PaymentFailed) {
                paymentStatus = "FAILED";
                log.info("Received PaymentFailed for order {}", orderId);
            } else {
                log.warn("Unknown event type on payments topic: {}", value.getClass().getName());
                return;
            }

            OrderStatusChanged result = aggregator.handlePaymentOutcome(orderId, paymentStatus, correlationId);
            if (result != null) {
                eventPublisher.publish(orderId, result);
            }

            idempotencyCache.mark(eventId);
        } finally {
            CorrelationIdUtils.clearMdc();
        }
    }
}
