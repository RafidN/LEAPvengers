package com.neueda.leap.model.dto;

/** Aggregated count of orders in one status for a date range. */
public interface OrderStatusCountProjection {
    String getOrderStatus();

    Long getCount();
}