package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What a trader's accounts were worth at one moment: cash plus holdings valued at the prices of that moment.
 */
public class AccountValuePoint {
    private LocalDateTime time;
    private BigDecimal totalValue;
    private BigDecimal cash;
    private BigDecimal holdingsValue;

    public AccountValuePoint() {
    }

    public AccountValuePoint(LocalDateTime time, BigDecimal cash, BigDecimal holdingsValue) {
        this.time = time;
        this.cash = cash;
        this.holdingsValue = holdingsValue;
        this.totalValue = cash.add(holdingsValue);
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getCash() {
        return cash;
    }

    public void setCash(BigDecimal cash) {
        this.cash = cash;
    }

    public BigDecimal getHoldingsValue() {
        return holdingsValue;
    }

    public void setHoldingsValue(BigDecimal holdingsValue) {
        this.holdingsValue = holdingsValue;
    }
}
