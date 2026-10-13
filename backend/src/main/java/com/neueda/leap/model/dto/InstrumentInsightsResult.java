package com.neueda.leap.model.dto;

import java.math.BigDecimal;

/**
 * Response DTO for instrument-level insights queries.
 */
public class InstrumentInsightsResult {
    private String ticker;
    private String instrumentName;
    private String assetClass;
    private BigDecimal currentPositionValue;
    private Long recentFilledOrderCount;
    private Long buyCount;
    private Long sellCount;
    private Long tradedClientCount;

    public InstrumentInsightsResult() {
    }

    public InstrumentInsightsResult(String ticker, String instrumentName, String assetClass,
                                    BigDecimal currentPositionValue, Long recentFilledOrderCount,
                                    Long buyCount, Long sellCount, Long tradedClientCount) {
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.assetClass = assetClass;
        this.currentPositionValue = currentPositionValue;
        this.recentFilledOrderCount = recentFilledOrderCount;
        this.buyCount = buyCount;
        this.sellCount = sellCount;
        this.tradedClientCount = tradedClientCount;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public BigDecimal getCurrentPositionValue() {
        return currentPositionValue;
    }

    public void setCurrentPositionValue(BigDecimal currentPositionValue) {
        this.currentPositionValue = currentPositionValue;
    }

    public Long getRecentFilledOrderCount() {
        return recentFilledOrderCount;
    }

    public void setRecentFilledOrderCount(Long recentFilledOrderCount) {
        this.recentFilledOrderCount = recentFilledOrderCount;
    }

    public Long getBuyCount() {
        return buyCount;
    }

    public void setBuyCount(Long buyCount) {
        this.buyCount = buyCount;
    }

    public Long getSellCount() {
        return sellCount;
    }

    public void setSellCount(Long sellCount) {
        this.sellCount = sellCount;
    }

    public Long getTradedClientCount() {
        return tradedClientCount;
    }

    public void setTradedClientCount(Long tradedClientCount) {
        this.tradedClientCount = tradedClientCount;
    }
}