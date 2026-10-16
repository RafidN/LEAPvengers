package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A filled order with the client and instrument details the reporting store copies (see OrderRepository). */
public interface FilledOrderRow {
    Integer getOrderId();

    LocalDateTime getExecutedAt();

    Integer getClientId();

    Integer getInstrumentId();

    String getTicker();

    String getAssetClass();

    String getMarket();

    String getSide();

    BigDecimal getQuantity();

    BigDecimal getPrice();
}
