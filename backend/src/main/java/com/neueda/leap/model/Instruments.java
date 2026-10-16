package com.neueda.leap.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "instruments")
public class Instruments {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Column(name = "ticker", nullable = false, unique = true)
    private String ticker;

    @Column(name = "instrument_name", nullable = false)
    private String instrumentName;

    @Column(name = "asset_class", nullable = false)
    private String assetClass;

    @Column(name = "market", nullable = false)
    private String market;

    // false while the trading desk has suspended the instrument; orders in it are rejected (BR-05)
    @Column(name = "tradable", nullable = false)
    private boolean tradable = true;

    // How many minutes behind real time this instrument's market data is (15 for London and NSE on Yahoo's
    // free feed); the current-price rule allows that much extra age
    @Column(name = "quote_delay_minutes", nullable = false)
    private int quoteDelayMinutes;

    // The lists below are left out of JSON: they are lazy, large, and point back at this instrument
    @JsonIgnore
    @OneToMany(mappedBy = "instrument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Holdings> holdings;

    @JsonIgnore
    @OneToMany(mappedBy = "instrument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Orders> orders;

    @JsonIgnore
    @OneToMany(mappedBy = "instrument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PriceQuotes> priceQuotes;

    // Constructors
    public Instruments() {
    }

    public Instruments(String ticker, String instrumentName, String assetClass, String market) {
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.assetClass = assetClass;
        this.market = market;
    }

    public Instruments(Integer instrumentId, String ticker, String instrumentName, String assetClass, String market) {
        this.instrumentId = instrumentId;
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.assetClass = assetClass;
        this.market = market;
    }

    // Getters and Setters
    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
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

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public boolean isTradable() {
        return tradable;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    public int getQuoteDelayMinutes() {
        return quoteDelayMinutes;
    }

    public void setQuoteDelayMinutes(int quoteDelayMinutes) {
        this.quoteDelayMinutes = quoteDelayMinutes;
    }

    public List<Holdings> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<Holdings> holdings) {
        this.holdings = holdings;
    }

    public List<Orders> getOrders() {
        return orders;
    }

    public void setOrders(List<Orders> orders) {
        this.orders = orders;
    }

    public List<PriceQuotes> getPriceQuotes() {
        return priceQuotes;
    }

    public void setPriceQuotes(List<PriceQuotes> priceQuotes) {
        this.priceQuotes = priceQuotes;
    }
}
