package com.example.payment.service;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.PaymentAuthorized;
import com.example.common.avro.PaymentFailed;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationService.class);
    private static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("1000");

    public SpecificRecordBase authorize(OrderCreated event) {
        BigDecimal total = new BigDecimal(event.getTotal());
        String orderId = event.getId();

        if (total.compareTo(APPROVAL_THRESHOLD) < 0) {
            log.info("Payment authorized for order {}: amount={}", orderId, total);
            return PaymentAuthorized.newBuilder()
                    .setOrderId(orderId)
                    .setAmount(total.toPlainString())
                    .setAuthorizedAt(Instant.now().toString())
                    .build();
        } else {
            log.info("Payment failed for order {}: amount={} exceeds threshold", orderId, total);
            return PaymentFailed.newBuilder()
                    .setOrderId(orderId)
                    .setReason("Amount " + total.toPlainString() + " exceeds approval threshold of " + APPROVAL_THRESHOLD)
                    .setFailedAt(Instant.now().toString())
                    .build();
        }
    }
}
