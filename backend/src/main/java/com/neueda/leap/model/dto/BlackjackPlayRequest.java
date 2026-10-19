package com.neueda.leap.model.dto;

import java.math.BigDecimal;

/**
 * Request payload for one blackjack hand.
 */
public class BlackjackPlayRequest {
    private Integer accountId;
    private String ticker;
    private BigDecimal betQuantity;

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getBetQuantity() {
        return betQuantity;
    }

    public void setBetQuantity(BigDecimal betQuantity) {
        this.betQuantity = betQuantity;
    }
}