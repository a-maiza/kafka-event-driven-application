package com.example.query.service;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.common.avro.OrderStatusChanged;
import com.example.query.model.OrderView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrderViewStoreTest {

    private OrderViewStore store;

    @BeforeEach
    void setUp() {
        store = new OrderViewStore();
    }

    @Test
    void createFromOrderCreated_shouldMapAllFields() {
        OrderCreated event = OrderCreated.newBuilder()
                .setId("order-1")
                .setCustomerId("cust-1")
                .setLines(List.of(new OrderLine("SKU-001", 2), new OrderLine("SKU-002", 3)))
                .setTotal("250")
                .setStatus("CREATED")
                .setCreatedAt("2025-01-01T00:00:00Z")
                .build();

        store.createFromOrderCreated(event);

        Optional<OrderView> result = store.findById("order-1");
        assertThat(result).isPresent();
        OrderView view = result.get();
        assertThat(view.getId()).isEqualTo("order-1");
        assertThat(view.getCustomerId()).isEqualTo("cust-1");
        assertThat(view.getTotal()).isEqualTo("250");
        assertThat(view.getStatus()).isEqualTo("CREATED");
        assertThat(view.getCreatedAt()).isEqualTo("2025-01-01T00:00:00Z");
        assertThat(view.getLines()).hasSize(2);
        assertThat(view.getLines().get(0).sku()).isEqualTo("SKU-001");
        assertThat(view.getLines().get(0).qty()).isEqualTo(2);
        assertThat(view.getLines().get(1).sku()).isEqualTo("SKU-002");
        assertThat(view.getLines().get(1).qty()).isEqualTo(3);
    }

    @Test
    void updateFromStatusChanged_shouldUpdateExistingView() {
        OrderCreated created = OrderCreated.newBuilder()
                .setId("order-1")
                .setCustomerId("cust-1")
                .setLines(List.of(new OrderLine("SKU-001", 1)))
                .setTotal("100")
                .setStatus("CREATED")
                .setCreatedAt("2025-01-01T00:00:00Z")
                .build();
        store.createFromOrderCreated(created);

        OrderStatusChanged statusChanged = OrderStatusChanged.newBuilder()
                .setOrderId("order-1")
                .setPaymentStatus("AUTHORIZED")
                .setInventoryStatus("RESERVED")
                .setFinalStatus("CONFIRMED")
                .setUpdatedAt("2025-01-01T00:01:00Z")
                .build();
        store.updateFromStatusChanged(statusChanged);

        OrderView view = store.findById("order-1").orElseThrow();
        assertThat(view.getPaymentStatus()).isEqualTo("AUTHORIZED");
        assertThat(view.getInventoryStatus()).isEqualTo("RESERVED");
        assertThat(view.getFinalStatus()).isEqualTo("CONFIRMED");
        assertThat(view.getUpdatedAt()).isEqualTo("2025-01-01T00:01:00Z");
    }

    @Test
    void updateFromStatusChanged_onUnknownId_shouldBeNoOp() {
        OrderStatusChanged statusChanged = OrderStatusChanged.newBuilder()
                .setOrderId("unknown-order")
                .setPaymentStatus("AUTHORIZED")
                .setInventoryStatus("RESERVED")
                .setFinalStatus("CONFIRMED")
                .setUpdatedAt("2025-01-01T00:01:00Z")
                .build();

        // Should not throw
        store.updateFromStatusChanged(statusChanged);

        assertThat(store.findById("unknown-order")).isEmpty();
    }

    @Test
    void findById_notFound_shouldReturnEmpty() {
        assertThat(store.findById("nonexistent")).isEmpty();
    }
}
