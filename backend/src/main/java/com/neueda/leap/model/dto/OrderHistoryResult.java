package com.neueda.leap.model.dto;

import com.neueda.leap.model.Orders;

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
    private String orderStatus;  // SUBMITTED, WORKING, ACCEPTED, FILLED, REJECTED, FAILED, CANCELED
    private LocalDate orderDate;
    private LocalDateTime submittedAt;
    private Integer accountId;
    private String rejectionReason;  // set for REJECTED, FAILED and CANCELED orders
    private BigDecimal executionPrice;  // set for FILLED orders
    private LocalDateTime executedAt;
    private String orderKind;  // MARKET, LIMIT or STOP
    private BigDecimal triggerPrice;  // the limit or stop price, for LIMIT and STOP orders
    private LocalDateTime triggeredAt;  // when a LIMIT or STOP order's price was reached

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

    public OrderHistoryResult(Integer orderId, String ticker, String orderType,
                             BigDecimal quantity, BigDecimal price, String orderStatus,
                             LocalDate orderDate, LocalDateTime submittedAt,
                             Integer accountId, String rejectionReason,
                             BigDecimal executionPrice, LocalDateTime executedAt) {
        this(orderId, ticker, orderType, quantity, price, orderStatus, orderDate, submittedAt);
        this.accountId = accountId;
        this.rejectionReason = rejectionReason;
        this.executionPrice = executionPrice;
        this.executedAt = executedAt;
    }

    public OrderHistoryResult(Integer orderId, String ticker, String orderType,
                             BigDecimal quantity, BigDecimal price, String orderStatus,
                             LocalDate orderDate, LocalDateTime submittedAt,
                             Integer accountId, String rejectionReason,
                             BigDecimal executionPrice, LocalDateTime executedAt,
                             String orderKind, BigDecimal triggerPrice, LocalDateTime triggeredAt) {
        this(orderId, ticker, orderType, quantity, price, orderStatus, orderDate, submittedAt, accountId,
                rejectionReason, executionPrice, executedAt);
        this.orderKind = orderKind;
        this.triggerPrice = triggerPrice;
        this.triggeredAt = triggeredAt;
    }

    /** An order as the trader sees it. */
    public static OrderHistoryResult of(Orders order, String ticker) {
        return new OrderHistoryResult(order.getOrderId(), ticker, order.getOrderType(), order.getQuantity(),
                order.getPrice(), order.getOrderStatus(), order.getOrderDate(), order.getSubmittedAt(),
                order.getAccountId(), order.getRejectionReason(), order.getExecutionPrice(), order.getExecutedAt(),
                order.getOrderKind(), order.getTriggerPrice(), order.getTriggeredAt());
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

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(BigDecimal executionPrice) {
        this.executionPrice = executionPrice;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public String getOrderKind() {
        return orderKind;
    }

    public void setOrderKind(String orderKind) {
        this.orderKind = orderKind;
    }

    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    public LocalDateTime getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(LocalDateTime triggeredAt) {
        this.triggeredAt = triggeredAt;
    }
}
