package com.neueda.leap.model.dto;

/**
 * Response DTO for ticker price quote results
 * Represents public market data for a specific ticker
 */
public class PriceQuoteResult {
    private String ticker;
    private String instrumentName;
    private Double currentPrice;
    private String assetClass;
    private String quoteTimestamp;

    public PriceQuoteResult() {
    }

    public PriceQuoteResult(String ticker, String instrumentName, Double currentPrice, 
                            String assetClass, String quoteTimestamp) {
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

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(Double currentPrice) {
        this.currentPrice = currentPrice;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public String getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public void setQuoteTimestamp(String quoteTimestamp) {
        this.quoteTimestamp = quoteTimestamp;
    }
}
