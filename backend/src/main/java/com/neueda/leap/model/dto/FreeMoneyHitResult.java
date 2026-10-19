package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Result payload for one Free Money hit deduction event. */
public class FreeMoneyHitResult {
    private Integer accountId;
    private String ticker;
    private BigDecimal quantityBefore;
    private BigDecimal quantityDeducted;
    private BigDecimal quantityAfter;
    private LocalDateTime processedAt;
    private String message;

    public FreeMoneyHitResult(Integer accountId,
                              String ticker,
                              BigDecimal quantityBefore,
                              BigDecimal quantityDeducted,
                              BigDecimal quantityAfter,
                              LocalDateTime processedAt,
                              String message) {
        this.accountId = accountId;
        this.ticker = ticker;
        this.quantityBefore = quantityBefore;
        this.quantityDeducted = quantityDeducted;
        this.quantityAfter = quantityAfter;
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

    public BigDecimal getQuantityDeducted() {
        return quantityDeducted;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public String getMessage() {
        return message;
    }
}
