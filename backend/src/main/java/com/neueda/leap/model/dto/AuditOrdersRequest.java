package com.neueda.leap.model.dto;

import java.time.LocalDate;

/**
 * Request DTO for /internal/audit/orders endpoint
 * Supports filtering by client, instrument, status, and date range
 */
public class AuditOrdersRequest {
    private Integer clientId;
    private String ticker;
    private String orderStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private int pageNumber;
    private int pageSize;

    public AuditOrdersRequest() {
        this.pageNumber = 1;
        this.pageSize = 20;
    }

    // Getters and Setters
    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
