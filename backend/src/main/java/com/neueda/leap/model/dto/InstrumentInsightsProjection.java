package com.neueda.leap.model.dto;

import java.math.BigDecimal;

public interface InstrumentInsightsProjection {
    String getTicker();

    String getInstrumentName();

    String getAssetClass();

    BigDecimal getCurrentPositionValue();

    Long getRecentFilledOrderCount();

    Long getBuyCount();

    Long getSellCount();

    Long getTradedClientCount();
}