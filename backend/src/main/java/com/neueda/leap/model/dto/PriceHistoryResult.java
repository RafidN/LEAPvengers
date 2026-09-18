package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for historical price quotes by time period
 * Represents a single price quote for a ticker at a point in time
 */
public class PriceHistoryResult {
    private String ticker;
    private String instrumentName;
    private BigDecimal price;
    private Long volume;
    private LocalDateTime quoteTimestamp;

    public PriceHistoryResult() {
    }

    public PriceHistoryResult(String ticker, String instrumentName, BigDecimal price,
                             Long volume, LocalDateTime quoteTimestamp) {
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.price = price;
        this.volume = volume;
        this.quoteTimestamp = quoteTimestamp;
    }

    // Getters and Setters
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public LocalDateTime getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public void setQuoteTimestamp(LocalDateTime quoteTimestamp) {
        this.quoteTimestamp = quoteTimestamp;
    }
}
