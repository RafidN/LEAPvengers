package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Daily order-health rates for trend charts. */
public class OrderHealthDailyRate {
    private LocalDate date;
    private long totalOrders;
    private long filledOrders;
    private long rejectedOrders;
    private long failedOrders;
    private BigDecimal successRate;
    private BigDecimal failureRate;

    public OrderHealthDailyRate() {
    }

    public OrderHealthDailyRate(LocalDate date, long totalOrders, long filledOrders,
                                long rejectedOrders, long failedOrders,
                                BigDecimal successRate, BigDecimal failureRate) {
        this.date = date;
        this.totalOrders = totalOrders;
        this.filledOrders = filledOrders;
        this.rejectedOrders = rejectedOrders;
        this.failedOrders = failedOrders;
        this.successRate = successRate;
        this.failureRate = failureRate;
    }

    public LocalDate getDate() {
        return date;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getFilledOrders() {
        return filledOrders;
    }

    public long getRejectedOrders() {
        return rejectedOrders;
    }

    public long getFailedOrders() {
        return failedOrders;
    }

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public BigDecimal getFailureRate() {
        return failureRate;
    }
}