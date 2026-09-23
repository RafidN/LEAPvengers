package com.neueda.leap.model.dto;

import java.math.BigDecimal;

public interface ClientSegmentMetricsProjection {
    Integer getClientId();

    String getFirstName();

    String getLastName();

    String getEmail();

    BigDecimal getTotalPortfolioValue();

    Long getRecentFilledOrderCount();
}