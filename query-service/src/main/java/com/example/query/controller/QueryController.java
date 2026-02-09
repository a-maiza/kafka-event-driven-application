package com.example.query.controller;

import com.example.query.controller.dto.OrderLineDto;
import com.example.query.controller.dto.OrderViewResponse;
import com.example.query.model.OrderView;
import com.example.query.service.OrderViewStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class QueryController {

    private final OrderViewStore orderViewStore;

    public QueryController(OrderViewStore orderViewStore) {
        this.orderViewStore = orderViewStore;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderViewResponse> getOrder(@PathVariable String id) {
        return orderViewStore.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private OrderViewResponse toResponse(OrderView view) {
        var lines = view.getLines().stream()
                .map(l -> new OrderLineDto(l.sku(), l.qty()))
                .toList();
        return new OrderViewResponse(
                view.getId(),
                view.getCustomerId(),
                lines,
                view.getTotal(),
                view.getStatus(),
                view.getCreatedAt(),
                view.getPaymentStatus(),
                view.getInventoryStatus(),
                view.getFinalStatus()
        );
    }
}
