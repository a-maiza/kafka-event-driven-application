package com.example.inventory.service;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderLine;
import com.example.common.avro.StockRejected;
import com.example.common.avro.StockReserved;
import org.apache.avro.specific.SpecificRecordBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final ConcurrentHashMap<String, Integer> stockLevels = new ConcurrentHashMap<>();

    public StockReservationService() {
        stockLevels.put("SKU-001", 100);
        stockLevels.put("SKU-002", 50);
        stockLevels.put("SKU-003", 200);
        stockLevels.put("SKU-004", 0);
        stockLevels.put("SKU-005", 10);
    }

    public synchronized SpecificRecordBase reserve(OrderCreated event) {
        String orderId = event.getId();
        List<OrderLine> lines = event.getLines();

        for (OrderLine line : lines) {
            String sku = line.getSku();
            int requested = line.getQty();
            int available = stockLevels.getOrDefault(sku, 0);

            if (available < requested) {
                log.info("Stock rejected for order {}: SKU {} has {} available but {} requested",
                        orderId, sku, available, requested);
                return StockRejected.newBuilder()
                        .setOrderId(orderId)
                        .setReason("Insufficient stock for SKU " + sku +
                                ": available=" + available + ", requested=" + requested)
                        .setRejectedAt(Instant.now().toString())
                        .build();
            }
        }

        for (OrderLine line : lines) {
            stockLevels.computeIfPresent(line.getSku(), (sku, qty) -> qty - line.getQty());
        }

        log.info("Stock reserved for order {}: {} line(s)", orderId, lines.size());
        return StockReserved.newBuilder()
                .setOrderId(orderId)
                .setLines(lines)
                .setReservedAt(Instant.now().toString())
                .build();
    }
}
