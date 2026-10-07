package com.neueda.leap.model.dto;

import java.util.List;

/**
 * Audit data wrapper containing paginated order results
 */
public class AuditData {
    private List<AuditOrder> orders;
    private int pageNumber;
    private int pageSize;
    private long totalRecords;
    private int totalPages;

    public AuditData() {
    }

    public AuditData(List<AuditOrder> orders, int pageNumber, int pageSize, long totalRecords) {
        this.orders = orders;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalRecords = totalRecords;
        this.totalPages = (int) Math.ceil((double) totalRecords / pageSize);
    }

    public List<AuditOrder> getOrders() {
        return orders;
    }

    public void setOrders(List<AuditOrder> orders) {
        this.orders = orders;
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

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}
