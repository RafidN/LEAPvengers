package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Response DTO for latest price quotes
 * Contains the current price and timestamp for an instrument, where the price came from, and whether the
 * instrument can be traded right now, so the order ticket can say so before an order is sent (BR-05, BR-13).
 */
public class QuoteResponse {
    private BigDecimal price;
    private LocalDateTime timestamp;
    private String source;              // YAHOO_LIVE, YAHOO_HISTORY or SIMULATED
    private int delayMinutes;           // how far behind real time this market's data is (0 = real time)
    private boolean tradable;           // the trading desk allows it and its market is open
    private boolean marketOpen;
    private String market;              // who sets the hours, e.g. "the London Stock Exchange"
    private OffsetDateTime opensAt;     // the next opening, when closed
    private OffsetDateTime closesAt;    // the coming close, when open (none for crypto)
    private String notTradableReason;   // the reason an order would be rejected now, when not tradable

    public QuoteResponse() {
    }

    public QuoteResponse(BigDecimal price, LocalDateTime timestamp) {
        this.price = price;
        this.timestamp = timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(int delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public boolean isTradable() {
        return tradable;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    public boolean isMarketOpen() {
        return marketOpen;
    }

    public void setMarketOpen(boolean marketOpen) {
        this.marketOpen = marketOpen;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public OffsetDateTime getOpensAt() {
        return opensAt;
    }

    public void setOpensAt(OffsetDateTime opensAt) {
        this.opensAt = opensAt;
    }

    public OffsetDateTime getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(OffsetDateTime closesAt) {
        this.closesAt = closesAt;
    }

    public String getNotTradableReason() {
        return notTradableReason;
    }

    public void setNotTradableReason(String notTradableReason) {
        this.notTradableReason = notTradableReason;
    }
}
