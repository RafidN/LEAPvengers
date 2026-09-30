package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for ticker price quote results
 * Represents public market data for a specific ticker
 */
public class InstrumentIdResult {
    private Integer instrumentId;

    public InstrumentIdResult() {
    }

    public InstrumentIdResult(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Integer getInstrumentId(){
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }
}