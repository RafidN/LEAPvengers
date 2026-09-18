package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for ticker price quote results
 * Represents public market data for a specific ticker
 */
public class PriceQuoteResult {
    private String ticker;
    private String instrumentName;
    private BigDecimal currentPrice;
    private String assetClass;
    private LocalDateTime quoteTimestamp;

    public PriceQuoteResult() {
    }

    public PriceQuoteResult(String ticker, String instrumentName, BigDecimal currentPrice,
                            String assetClass, LocalDateTime quoteTimestamp) {
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.currentPrice = currentPrice;
        this.assetClass = assetClass;
        this.quoteTimestamp = quoteTimestamp;
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

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public LocalDateTime getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public void setQuoteTimestamp(LocalDateTime quoteTimestamp) {
        this.quoteTimestamp = quoteTimestamp;
    }
}
