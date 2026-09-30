package com.neueda.leap.model.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for order event history.
 */
public class OrderEventResult {
    private Integer eventId;
    private Integer orderId;
    private Integer createdBy;
    private String createdByUsername;
    private String fromStatus;
    private String toStatus;
    private LocalDateTime createdAt;
    private String details;

    public OrderEventResult() {
    }

    public OrderEventResult(Integer eventId, Integer orderId, Integer createdBy, String createdByUsername,
                            String fromStatus, String toStatus, LocalDateTime createdAt, String details) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.createdBy = createdBy;
        this.createdByUsername = createdByUsername;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.createdAt = createdAt;
        this.details = details;
    }

    public Integer getEventId() {
        return eventId;
    }

    public void setEventId(Integer eventId) {
        this.eventId = eventId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}