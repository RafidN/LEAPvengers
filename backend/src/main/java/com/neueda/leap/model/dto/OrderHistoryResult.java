package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for order history by time period
 * Represents a single order placed by user
 */
public class OrderHistoryResult {
    private Integer orderId;
    private String ticker;
    private String orderType;  // BUY or SELL
    private BigDecimal quantity;
    private BigDecimal price;
    private String orderStatus;  // Pending, Filled, Canceled, Rejected
    private LocalDate orderDate;
    private LocalDateTime submittedAt;

    public OrderHistoryResult() {
    }

    public OrderHistoryResult(Integer orderId, String ticker, String orderType,
                             BigDecimal quantity, BigDecimal price, String orderStatus,
                             LocalDate orderDate, LocalDateTime submittedAt) {
        this.orderId = orderId;
        this.ticker = ticker;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.orderStatus = orderStatus;
        this.orderDate = orderDate;
        this.submittedAt = submittedAt;
    }

    // Getters and Setters
    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
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
}
