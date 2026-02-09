package com.example.inventory.service;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.common.avro.StockRejected;
import com.example.common.avro.StockReserved;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockReservationServiceTest {

    private StockReservationService service;

    @BeforeEach
    void setUp() {
        service = new StockReservationService();
    }

    private OrderCreated buildOrder(String id, List<OrderLine> lines) {
        return OrderCreated.newBuilder()
                .setId(id)
                .setCustomerId("cust-1")
                .setLines(lines)
                .setTotal("100")
                .setStatus("CREATED")
                .setCreatedAt("2025-01-01T00:00:00Z")
                .build();
    }

    @Test
    void reserve_availableStock_shouldReturnStockReserved() {
        OrderCreated order = buildOrder("order-1",
                List.of(new OrderLine("SKU-001", 10)));

        SpecificRecordBase result = service.reserve(order);

        assertThat(result).isInstanceOf(StockReserved.class);
        StockReserved reserved = (StockReserved) result;
        assertThat(reserved.getOrderId()).isEqualTo("order-1");
        assertThat(reserved.getLines()).hasSize(1);
        assertThat(reserved.getReservedAt()).isNotNull();
    }

    @Test
    void reserve_insufficientStock_shouldReturnStockRejected() {
        OrderCreated order = buildOrder("order-2",
                List.of(new OrderLine("SKU-001", 101)));

        SpecificRecordBase result = service.reserve(order);

        assertThat(result).isInstanceOf(StockRejected.class);
        StockRejected rejected = (StockRejected) result;
        assertThat(rejected.getOrderId()).isEqualTo("order-2");
        assertThat(rejected.getReason()).contains("SKU-001");
    }

    @Test
    void reserve_unknownSku_shouldReturnStockRejected() {
        OrderCreated order = buildOrder("order-3",
                List.of(new OrderLine("SKU-UNKNOWN", 1)));

        SpecificRecordBase result = service.reserve(order);

        assertThat(result).isInstanceOf(StockRejected.class);
        StockRejected rejected = (StockRejected) result;
        assertThat(rejected.getReason()).contains("SKU-UNKNOWN");
    }

    @Test
    void reserve_zeroStockSku_shouldReturnStockRejected() {
        OrderCreated order = buildOrder("order-4",
                List.of(new OrderLine("SKU-004", 1)));

        SpecificRecordBase result = service.reserve(order);

        assertThat(result).isInstanceOf(StockRejected.class);
    }

    @Test
    void reserve_multiLineWithOneFail_shouldNotDeductAnyStock() {
        // First line OK (SKU-001:100), second line fails (SKU-004:0)
        OrderCreated order = buildOrder("order-5",
                List.of(new OrderLine("SKU-001", 10), new OrderLine("SKU-004", 1)));

        SpecificRecordBase result = service.reserve(order);
        assertThat(result).isInstanceOf(StockRejected.class);

        // SKU-001 should still have full stock (all-or-nothing)
        OrderCreated verifyOrder = buildOrder("order-6",
                List.of(new OrderLine("SKU-001", 100)));
        SpecificRecordBase verifyResult = service.reserve(verifyOrder);
        assertThat(verifyResult).isInstanceOf(StockReserved.class);
    }

    @Test
    void reserve_shouldDecrementStockAfterSuccess() {
        // Reserve 90 out of 100 for SKU-001
        OrderCreated first = buildOrder("order-7",
                List.of(new OrderLine("SKU-001", 90)));
        assertThat(service.reserve(first)).isInstanceOf(StockReserved.class);

        // Now only 10 left — requesting 11 should fail
        OrderCreated second = buildOrder("order-8",
                List.of(new OrderLine("SKU-001", 11)));
        assertThat(service.reserve(second)).isInstanceOf(StockRejected.class);

        // Requesting 10 should succeed
        OrderCreated third = buildOrder("order-9",
                List.of(new OrderLine("SKU-001", 10)));
        assertThat(service.reserve(third)).isInstanceOf(StockReserved.class);
    }

    @Test
    void reserve_multipleSkus_allAvailable_shouldReserve() {
        OrderCreated order = buildOrder("order-10",
                List.of(new OrderLine("SKU-001", 5),
                        new OrderLine("SKU-002", 5),
                        new OrderLine("SKU-003", 5)));

        SpecificRecordBase result = service.reserve(order);

        assertThat(result).isInstanceOf(StockReserved.class);
        StockReserved reserved = (StockReserved) result;
        assertThat(reserved.getLines()).hasSize(3);
    }
}
