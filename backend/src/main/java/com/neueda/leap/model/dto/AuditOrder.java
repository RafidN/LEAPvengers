package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit order response DTO for /internal/audit/orders
 * Contains order details with client and instrument information
 */
public class AuditOrder {
    private Integer orderId;
    private Integer clientId;
    private String clientName;
    private String ticker;
    private String instrumentName;
    private String orderType;
    private BigDecimal quantity;
    private BigDecimal price;
    private String orderStatus;
    private LocalDate orderDate;
    private LocalDateTime submittedAt;
    private LocalDateTime executedAt;

    public AuditOrder() {
    }

    public AuditOrder(Integer orderId, Integer clientId, String clientName, String ticker, 
                      String instrumentName, String orderType, BigDecimal quantity, 
                      BigDecimal price, String orderStatus, LocalDate orderDate, 
                      LocalDateTime submittedAt, LocalDateTime executedAt) {
        this.orderId = orderId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.orderStatus = orderStatus;
        this.orderDate = orderDate;
        this.submittedAt = submittedAt;
        this.executedAt = executedAt;
    }

    // Getters and Setters
    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
