package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for cash transaction history by time period
 * Represents a single cash movement: a deposit, a withdrawal, or the cash side of a filled order
 */
public class CashTransactionResult {
    private Integer cashTransactionId;
    private String transactionType;  // DEPOSIT, WITHDRAWAL, or BUY / SELL for a filled order
    private BigDecimal amount;
    private LocalDate transactionDate;
    private LocalDateTime transactionTime;  // when the cash moved
    private Double runningBalance;  // Optional: balance after transaction

    public CashTransactionResult() {
    }

    public CashTransactionResult(Integer cashTransactionId, String transactionType,
                                 BigDecimal amount, LocalDate transactionDate) {
        this.cashTransactionId = cashTransactionId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
    }

    public CashTransactionResult(Integer cashTransactionId, String transactionType,
                                 BigDecimal amount, LocalDate transactionDate, LocalDateTime transactionTime) {
        this(cashTransactionId, transactionType, amount, transactionDate);
        this.transactionTime = transactionTime;
    }

    public CashTransactionResult(Integer cashTransactionId, String transactionType,
                                 BigDecimal amount, LocalDate transactionDate, Double runningBalance) {
        this.cashTransactionId = cashTransactionId;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.runningBalance = runningBalance;
    }

    // Getters and Setters
    public Integer getCashTransactionId() {
        return cashTransactionId;
    }

    public void setCashTransactionId(Integer cashTransactionId) {
        this.cashTransactionId = cashTransactionId;
    }

    public LocalDateTime getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(LocalDateTime transactionTime) {
        this.transactionTime = transactionTime;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public Double getRunningBalance() {
        return runningBalance;
    }

    public void setRunningBalance(Double runningBalance) {
        this.runningBalance = runningBalance;
    }
}
