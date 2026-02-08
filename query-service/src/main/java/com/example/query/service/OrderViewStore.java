package com.example.query.service;

import com.example.common.avro.OrderCreated;
import com.example.common.avro.OrderStatusChanged;
import com.example.query.model.OrderView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderViewStore {

    private static final Logger log = LoggerFactory.getLogger(OrderViewStore.class);

    private final ConcurrentHashMap<String, OrderView> store = new ConcurrentHashMap<>();

    public void createFromOrderCreated(OrderCreated event) {
        OrderView view = new OrderView();
        view.setId(event.getId());
        view.setCustomerId(event.getCustomerId());
        view.setLines(event.getLines().stream()
                .map(l -> new OrderView.OrderViewLine(l.getSku(), l.getQty()))
                .toList());
        view.setTotal(event.getTotal());
        view.setStatus(event.getStatus());
        view.setCreatedAt(event.getCreatedAt());

        store.put(event.getId(), view);
        log.info("Materialized view created for order {}", event.getId());
    }

    public void updateFromStatusChanged(OrderStatusChanged event) {
        store.computeIfPresent(event.getOrderId(), (key, view) -> {
            view.setPaymentStatus(event.getPaymentStatus());
            view.setInventoryStatus(event.getInventoryStatus());
            view.setFinalStatus(event.getFinalStatus());
            view.setUpdatedAt(event.getUpdatedAt());
            log.info("Materialized view updated for order {}: finalStatus={}",
                    event.getOrderId(), event.getFinalStatus());
            return view;
        });
    }

    public Optional<OrderView> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
