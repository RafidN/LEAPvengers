package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A client's dispute of one of their orders. Raised OPEN from the History page, then RESOLVED once by
 * Operations with a written resolution. The database stops anything else changing (order_disputes trigger).
 */
@Entity
@Table(name = "order_disputes")
public class OrderDisputes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dispute_id")
    private Integer disputeId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "raised_by", nullable = false)
    private Integer raisedBy;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolution", length = 1000)
    private String resolution;

    @Column(name = "resolved_by")
    private Integer resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public OrderDisputes() {
    }

    public OrderDisputes(Integer orderId, Integer raisedBy, String reason, String message) {
        this.orderId = orderId;
        this.raisedBy = raisedBy;
        this.reason = reason;
        this.message = message;
        this.status = "OPEN";
        this.createdAt = LocalDateTime.now();
    }

    /** Ops' answer; the dispute is closed for good. */
    public void resolve(Integer resolvedBy, String resolution) {
        this.status = "RESOLVED";
        this.resolvedBy = resolvedBy;
        this.resolution = resolution;
        this.resolvedAt = LocalDateTime.now();
    }

    public Integer getDisputeId() {
        return disputeId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public Integer getRaisedBy() {
        return raisedBy;
    }

    public String getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getResolution() {
        return resolution;
    }

    public Integer getResolvedBy() {
        return resolvedBy;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }
}
