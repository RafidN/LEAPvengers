package com.neueda.leap.model.dto;

import java.time.LocalDate;

/** Aggregated order-status count for one order date. */
public interface OrderStatusCountByDateProjection {
    LocalDate getOrderDate();

    String getOrderStatus();

    Long getCount();
}