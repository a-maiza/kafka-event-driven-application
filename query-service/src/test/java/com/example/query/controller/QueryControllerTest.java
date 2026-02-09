package com.example.query.controller;

import com.example.query.model.OrderView;
import com.example.query.service.OrderViewStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueryController.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderViewStore orderViewStore;

    @Test
    void getOrder_found_shouldReturn200WithResponse() throws Exception {
        OrderView view = new OrderView();
        view.setId("order-1");
        view.setCustomerId("cust-1");
        view.setLines(List.of(new OrderView.OrderViewLine("SKU-001", 2)));
        view.setTotal("200");
        view.setStatus("CREATED");
        view.setCreatedAt("2025-01-01T00:00:00Z");
        view.setPaymentStatus("AUTHORIZED");
        view.setInventoryStatus("RESERVED");
        view.setFinalStatus("CONFIRMED");

        when(orderViewStore.findById("order-1")).thenReturn(Optional.of(view));

        mockMvc.perform(get("/orders/order-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("order-1"))
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andExpect(jsonPath("$.total").value("200"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.paymentStatus").value("AUTHORIZED"))
                .andExpect(jsonPath("$.inventoryStatus").value("RESERVED"))
                .andExpect(jsonPath("$.finalStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.lines[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.lines[0].qty").value(2));
    }

    @Test
    void getOrder_notFound_shouldReturn404() throws Exception {
        when(orderViewStore.findById("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/orders/unknown"))
                .andExpect(status().isNotFound());
    }
}
