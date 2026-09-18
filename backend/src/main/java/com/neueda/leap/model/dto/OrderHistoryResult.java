package com.neueda.leap.model.dto;

/**
 * Response DTO for order history by time period
 * Represents a single order placed by user
 */
public class OrderHistoryResult {
    private Integer orderId;
    private String ticker;
    private String orderType;  // BUY or SELL
    private Long quantity;
    private Double price;
    private String orderStatus;  // Pending, Filled, Canceled, Rejected
    private String orderDate;
    private String submittedAt;

    public OrderHistoryResult() {
    }

    public OrderHistoryResult(Integer orderId, String ticker, String orderType, 
                             Long quantity, Double price, String orderStatus, 
                             String orderDate, String submittedAt) {
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

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(String orderDate) {
        this.orderDate = orderDate;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }
}
