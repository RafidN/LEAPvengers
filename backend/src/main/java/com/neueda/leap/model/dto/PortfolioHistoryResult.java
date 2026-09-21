package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for portfolio holdings as of a specific date
 * Represents a snapshot of a user's holdings at a point in time
 */
public class PortfolioHistoryResult {
    private Integer holdingId;
    private String ticker;
    private String instrumentName;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalValue;  // quantity * price
    private LocalDate asOfDate;

    public PortfolioHistoryResult() {
    }

    public PortfolioHistoryResult(Integer holdingId, String ticker, String instrumentName,
                                  BigDecimal quantity, BigDecimal price, BigDecimal totalValue, LocalDate asOfDate) {
        this.holdingId = holdingId;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.price = price;
        this.totalValue = totalValue;
        this.asOfDate = asOfDate;
    }

    // Getters and Setters
    public Integer getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Integer holdingId) {
        this.holdingId = holdingId;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }
}
