package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Result payload for one Free Money boss defeat reward event. */
public class FreeMoneyWinResult {
    private Integer accountId;
    private String ticker;
    private BigDecimal quantityBefore;
    private BigDecimal quantityAfter;
    private BigDecimal multiplier;
    private LocalDateTime processedAt;
    private String message;

    public FreeMoneyWinResult(Integer accountId,
                              String ticker,
                              BigDecimal quantityBefore,
                              BigDecimal quantityAfter,
                              BigDecimal multiplier,
                              LocalDateTime processedAt,
                              String message) {
        this.accountId = accountId;
        this.ticker = ticker;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.multiplier = multiplier;
        this.processedAt = processedAt;
        this.message = message;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getTicker() {
        return ticker;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getMessage() {
        return message;
    }
}
