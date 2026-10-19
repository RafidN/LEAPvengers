package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Result payload for initializing a Free Money battle session. */
public class FreeMoneyBattleStartResult {
    private String battleId;
    private Integer accountId;
    private String ticker;
    private BigDecimal startingQuantity;
    private LocalDateTime startedAt;
    private String message;

    public FreeMoneyBattleStartResult(String battleId,
                                      Integer accountId,
                                      String ticker,
                                      BigDecimal startingQuantity,
                                      LocalDateTime startedAt,
                                      String message) {
        this.battleId = battleId;
        this.accountId = accountId;
        this.ticker = ticker;
        this.startingQuantity = startingQuantity;
        this.startedAt = startedAt;
        this.message = message;
    }

    public String getBattleId() {
        return battleId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getStartingQuantity() {
        return startingQuantity;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public String getMessage() {
        return message;
    }
}
