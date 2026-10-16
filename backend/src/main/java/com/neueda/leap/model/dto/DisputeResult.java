package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A dispute with what both sides need to see: the order it is about, what the client said, and Ops' resolution.
 * Client name and id are only filled in for Operations; a trader's own view leaves them out.
 */
public class DisputeResult {
    private Integer disputeId;
    private Integer orderId;
    private Integer clientId;
    private String clientName;
    private String ticker;
    private String orderType;
    private BigDecimal quantity;
    private String orderStatus;
    private String reason;          // WRONG_PRICE, SHOULD_HAVE_FILLED, NOT_PLACED_BY_ME or OTHER
    private String message;
    private String status;          // OPEN or RESOLVED
    private LocalDateTime createdAt;
    private String raisedByUsername;
    private String resolution;
    private LocalDateTime resolvedAt;
    private String resolvedByUsername;

    public DisputeResult() {
    }

    public DisputeResult(Integer disputeId, Integer orderId, Integer clientId, String clientName, String ticker,
                         String orderType, BigDecimal quantity, String orderStatus, String reason, String message,
                         String status, LocalDateTime createdAt, String raisedByUsername, String resolution,
                         LocalDateTime resolvedAt, String resolvedByUsername) {
        this.disputeId = disputeId;
        this.orderId = orderId;
        this.clientId = clientId;
        this.clientName = clientName;
        this.ticker = ticker;
        this.orderType = orderType;
        this.quantity = quantity;
        this.orderStatus = orderStatus;
        this.reason = reason;
        this.message = message;
        this.status = status;
        this.createdAt = createdAt;
        this.raisedByUsername = raisedByUsername;
        this.resolution = resolution;
        this.resolvedAt = resolvedAt;
        this.resolvedByUsername = resolvedByUsername;
    }

    /** The trader's own view: no client details, and staff are just "Operations". */
    public DisputeResult forClient() {
        return new DisputeResult(disputeId, orderId, null, null, ticker, orderType, quantity, orderStatus, reason,
                message, status, createdAt, raisedByUsername, resolution, resolvedAt,
                resolvedByUsername == null ? null : "Operations");
    }

    public Integer getDisputeId() { return disputeId; }
    public Integer getOrderId() { return orderId; }
    public Integer getClientId() { return clientId; }
    public String getClientName() { return clientName; }
    public String getTicker() { return ticker; }
    public String getOrderType() { return orderType; }
    public BigDecimal getQuantity() { return quantity; }
    public String getOrderStatus() { return orderStatus; }
    public String getReason() { return reason; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRaisedByUsername() { return raisedByUsername; }
    public String getResolution() { return resolution; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public String getResolvedByUsername() { return resolvedByUsername; }
}
