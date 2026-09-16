package com.neueda.leap.model.dto;

/**
 * Request DTO for ticker symbol search
 */
public class TickerSearchRequest {
    private String ticker;

    public TickerSearchRequest() {
    }

    public TickerSearchRequest(String ticker) {
        this.ticker = ticker;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }
}
