package com.neueda.leap.model.dto;

/**
 * Response DTO for ticker search results
 */
public class TickerSearchResult {
    private Integer holdingId;
    private String ticker;
    private String instrumentName;
    private Long quantity;
    private Double marketValue;

    public TickerSearchResult() {
    }

    public TickerSearchResult(Integer holdingId, String ticker, String instrumentName, 
                              Long quantity, Double marketValue) {
        this.holdingId = holdingId;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.quantity = quantity;
        this.marketValue = marketValue;
    }

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

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Double getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(Double marketValue) {
        this.marketValue = marketValue;
    }
}
