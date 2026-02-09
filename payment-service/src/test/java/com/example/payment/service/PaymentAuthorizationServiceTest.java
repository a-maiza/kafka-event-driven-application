package com.example.payment.service;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.common.avro.PaymentAuthorized;
import com.example.common.avro.PaymentFailed;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAuthorizationServiceTest {

    private PaymentAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new PaymentAuthorizationService();
    }

    private OrderCreated buildOrder(String id, String total) {
        return OrderCreated.newBuilder()
                .setId(id)
                .setCustomerId("cust-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setTotal(total)
                .setStatus("CREATED")
                .setCreatedAt("2025-01-01T00:00:00Z")
                .build();
    }

    @Test
    void authorize_belowThreshold_shouldReturnPaymentAuthorized() {
        SpecificRecordBase result = service.authorize(buildOrder("order-1", "999"));

        assertThat(result).isInstanceOf(PaymentAuthorized.class);
        PaymentAuthorized authorized = (PaymentAuthorized) result;
        assertThat(authorized.getOrderId()).isEqualTo("order-1");
        assertThat(authorized.getAmount()).isEqualTo("999");
        assertThat(authorized.getAuthorizedAt()).isNotNull();
    }

    @Test
    void authorize_atThreshold_shouldReturnPaymentFailed() {
        SpecificRecordBase result = service.authorize(buildOrder("order-2", "1000"));

        assertThat(result).isInstanceOf(PaymentFailed.class);
        PaymentFailed failed = (PaymentFailed) result;
        assertThat(failed.getOrderId()).isEqualTo("order-2");
        assertThat(failed.getReason()).contains("1000");
    }

    @Test
    void authorize_aboveThreshold_shouldReturnPaymentFailed() {
        SpecificRecordBase result = service.authorize(buildOrder("order-3", "5000"));

        assertThat(result).isInstanceOf(PaymentFailed.class);
    }

    @Test
    void authorize_zeroAmount_shouldReturnPaymentAuthorized() {
        SpecificRecordBase result = service.authorize(buildOrder("order-4", "0"));

        assertThat(result).isInstanceOf(PaymentAuthorized.class);
    }

    @Test
    void authorize_justBelowThreshold_shouldReturnPaymentAuthorized() {
        SpecificRecordBase result = service.authorize(buildOrder("order-5", "999.99"));

        assertThat(result).isInstanceOf(PaymentAuthorized.class);
    }
}
