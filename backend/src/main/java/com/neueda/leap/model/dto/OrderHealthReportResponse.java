package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Operational order-health metrics over a date range. */
public class OrderHealthReportResponse {
    private LocalDate from;
    private LocalDate to;
    private long totalOrders;
    private long submittedOrders;
    private long acceptedOrders;
    private long filledOrders;
    private long rejectedOrders;
    private long failedOrders;
    private BigDecimal successRate;
    private BigDecimal failureRate;
    private List<OrderHealthStatusCount> statusCounts;
    private List<OrderHealthDailyRate> dailyRates;

    public OrderHealthReportResponse() {
    }

    public OrderHealthReportResponse(LocalDate from, LocalDate to, long totalOrders,
                                     long submittedOrders, long acceptedOrders,
                                     long filledOrders, long rejectedOrders, long failedOrders,
                                     BigDecimal successRate, BigDecimal failureRate,
                                     List<OrderHealthStatusCount> statusCounts,
                                     List<OrderHealthDailyRate> dailyRates) {
        this.from = from;
        this.to = to;
        this.totalOrders = totalOrders;
        this.submittedOrders = submittedOrders;
        this.acceptedOrders = acceptedOrders;
        this.filledOrders = filledOrders;
        this.rejectedOrders = rejectedOrders;
        this.failedOrders = failedOrders;
        this.successRate = successRate;
        this.failureRate = failureRate;
        this.statusCounts = statusCounts;
        this.dailyRates = dailyRates;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getSubmittedOrders() {
        return submittedOrders;
    }

    public long getAcceptedOrders() {
        return acceptedOrders;
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

    public List<OrderHealthStatusCount> getStatusCounts() {
        return statusCounts;
    }

    public List<OrderHealthDailyRate> getDailyRates() {
        return dailyRates;
    }
}