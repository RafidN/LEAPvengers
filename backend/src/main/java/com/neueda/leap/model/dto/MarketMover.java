package com.neueda.leap.model.dto;

import java.math.BigDecimal;

/**
 * A US stock among today's biggest gainers or losers, as listed by Yahoo Finance.
 * These are market-wide stocks, not necessarily instruments that can be traded in this app.
 */
public class MarketMover {
    private String ticker;
    private String name;
    private BigDecimal price;
    private BigDecimal change;
    private BigDecimal changePercent;   // e.g. 10.82 for +10.82%
    private Long volume;
    private String marketCap;           // as Yahoo shows it, e.g. "3.052B"

    public MarketMover() {
    }

    public MarketMover(String ticker, String name, BigDecimal price, BigDecimal change, BigDecimal changePercent,
                       Long volume, String marketCap) {
        this.ticker = ticker;
        this.name = name;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.volume = volume;
        this.marketCap = marketCap;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getChange() {
        return change;
    }

    public void setChange(BigDecimal change) {
        this.change = change;
    }

    public BigDecimal getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public String getMarketCap() {
        return marketCap;
    }

    public void setMarketCap(String marketCap) {
        this.marketCap = marketCap;
    }
}
