package com.neueda.leap.model.dto;

import java.math.BigDecimal;

/**
 * Trading in one group of a report: a day, week or month, an instrument, an asset class or a client segment.
 * Aggregates only; no client is identified.
 */
public class ActivityReportRow {
    private String key;            // e.g. 2026-10-12, NFLX, Crypto, Premier
    private String label;          // for people, e.g. "Week of Oct 12, 2026"
    private long trades;
    private long buys;
    private long sells;
    private BigDecimal tradedValue;    // sum of quantity x execution price, USD
    private long activeClients;        // distinct clients who traded in the group

    public ActivityReportRow() {
    }

    public ActivityReportRow(String key, String label, long trades, long buys, BigDecimal tradedValue, long activeClients) {
        this.key = key;
        this.label = label;
        this.trades = trades;
        this.buys = buys;
        this.sells = trades - buys;
        this.tradedValue = tradedValue;
        this.activeClients = activeClients;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public long getTrades() {
        return trades;
    }

    public long getBuys() {
        return buys;
    }

    public long getSells() {
        return sells;
    }

    public BigDecimal getTradedValue() {
        return tradedValue;
    }

    public long getActiveClients() {
        return activeClients;
    }
}
