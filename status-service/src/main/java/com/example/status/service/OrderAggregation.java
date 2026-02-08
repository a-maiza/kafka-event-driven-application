package com.example.status.service;

public class OrderAggregation {

    private String paymentStatus;
    private String inventoryStatus;
    private String correlationId;

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getInventoryStatus() {
        return inventoryStatus;
    }

    public void setInventoryStatus(String inventoryStatus) {
        this.inventoryStatus = inventoryStatus;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        if (this.correlationId == null) {
            this.correlationId = correlationId;
        }
    }

    public boolean isComplete() {
        return paymentStatus != null && inventoryStatus != null;
    }
}
