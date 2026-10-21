package com.neueda.leap.model.dto;

/** Status count row for order-health analytics. */
public class OrderHealthStatusCount {
    private String status;
    private long count;

    public OrderHealthStatusCount() {
    }

    public OrderHealthStatusCount(String status, long count) {
        this.status = status;
        this.count = count;
    }

    public String getStatus() {
        return status;
    }

    public long getCount() {
        return count;
    }
}