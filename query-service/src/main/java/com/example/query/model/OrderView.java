package com.example.query.model;

import java.util.List;

public class OrderView {

    private String id;
    private String customerId;
    private List<OrderViewLine> lines;
    private String total;
    private String status;
    private String createdAt;
    private String paymentStatus;
    private String inventoryStatus;
    private String finalStatus;
    private String updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public List<OrderViewLine> getLines() { return lines; }
    public void setLines(List<OrderViewLine> lines) { this.lines = lines; }

    public String getTotal() { return total; }
    public void setTotal(String total) { this.total = total; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getInventoryStatus() { return inventoryStatus; }
    public void setInventoryStatus(String inventoryStatus) { this.inventoryStatus = inventoryStatus; }

    public String getFinalStatus() { return finalStatus; }
    public void setFinalStatus(String finalStatus) { this.finalStatus = finalStatus; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public record OrderViewLine(String sku, int qty) {}
}
