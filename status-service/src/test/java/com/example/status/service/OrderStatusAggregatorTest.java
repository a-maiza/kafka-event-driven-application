package com.example.status.service;

import com.example.common.avro.OrderStatusChanged;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusAggregatorTest {

    private OrderStatusAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new OrderStatusAggregator();
    }

    @Test
    void paymentFirst_thenInventory_shouldReturnStatusChanged() {
        OrderStatusChanged afterPayment = aggregator.handlePaymentOutcome("order-1", "AUTHORIZED", "corr-1");
        assertThat(afterPayment).isNull();

        OrderStatusChanged afterInventory = aggregator.handleInventoryOutcome("order-1", "RESERVED", "corr-1");
        assertThat(afterInventory).isNotNull();
        assertThat(afterInventory.getOrderId()).isEqualTo("order-1");
        assertThat(afterInventory.getFinalStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void inventoryFirst_thenPayment_shouldReturnStatusChanged() {
        OrderStatusChanged afterInventory = aggregator.handleInventoryOutcome("order-2", "RESERVED", "corr-2");
        assertThat(afterInventory).isNull();

        OrderStatusChanged afterPayment = aggregator.handlePaymentOutcome("order-2", "AUTHORIZED", "corr-2");
        assertThat(afterPayment).isNotNull();
        assertThat(afterPayment.getFinalStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void authorizedAndReserved_shouldBeConfirmed() {
        aggregator.handlePaymentOutcome("order-3", "AUTHORIZED", "corr-3");
        OrderStatusChanged result = aggregator.handleInventoryOutcome("order-3", "RESERVED", "corr-3");

        assertThat(result.getFinalStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getPaymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(result.getInventoryStatus()).isEqualTo("RESERVED");
    }

    @Test
    void authorizedAndRejected_shouldBeRejected() {
        aggregator.handlePaymentOutcome("order-4", "AUTHORIZED", "corr-4");
        OrderStatusChanged result = aggregator.handleInventoryOutcome("order-4", "REJECTED", "corr-4");

        assertThat(result.getFinalStatus()).isEqualTo("REJECTED");
    }

    @Test
    void failedAndReserved_shouldBeRejected() {
        aggregator.handlePaymentOutcome("order-5", "FAILED", "corr-5");
        OrderStatusChanged result = aggregator.handleInventoryOutcome("order-5", "RESERVED", "corr-5");

        assertThat(result.getFinalStatus()).isEqualTo("REJECTED");
    }

    @Test
    void failedAndRejected_shouldBeRejected() {
        aggregator.handlePaymentOutcome("order-6", "FAILED", "corr-6");
        OrderStatusChanged result = aggregator.handleInventoryOutcome("order-6", "REJECTED", "corr-6");

        assertThat(result.getFinalStatus()).isEqualTo("REJECTED");
    }

    @Test
    void aggregation_shouldBeCleanedUpAfterCompletion() {
        aggregator.handlePaymentOutcome("order-7", "AUTHORIZED", "corr-7");
        aggregator.handleInventoryOutcome("order-7", "RESERVED", "corr-7");

        // Second round for same orderId should start fresh (null after first outcome)
        OrderStatusChanged secondRound = aggregator.handlePaymentOutcome("order-7", "FAILED", "corr-8");
        assertThat(secondRound).isNull();
    }

    @Test
    void result_shouldHaveUpdatedAt() {
        aggregator.handlePaymentOutcome("order-8", "AUTHORIZED", "corr-8");
        OrderStatusChanged result = aggregator.handleInventoryOutcome("order-8", "RESERVED", "corr-8");

        assertThat(result.getUpdatedAt()).isNotNull();
    }
}
