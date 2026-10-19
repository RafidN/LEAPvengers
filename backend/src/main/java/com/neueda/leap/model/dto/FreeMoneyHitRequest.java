package com.neueda.leap.model.dto;

/** Request payload for one Free Money bullet hit deduction. */
public class FreeMoneyHitRequest {
    private String battleId;
    private Integer accountId;
    private String ticker;

    public String getBattleId() {
        return battleId;
    }

    public void setBattleId(String battleId) {
        this.battleId = battleId;
    }

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
}
