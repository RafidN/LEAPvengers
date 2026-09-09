package com.neueda.leap.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cash_transactions")
public class CashTransactions {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cash_transaction_id")
    private Integer cashTransactionId;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Accounts account;

    @Column(name = "txn_type", nullable = false)
    private String txnType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 4)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    // Constructors
    public CashTransactions() {
    }

    public CashTransactions(Integer accountId, String txnType, BigDecimal amount, LocalDate txnDate) {
        this.accountId = accountId;
        this.txnType = txnType;
        this.amount = amount;
        this.txnDate = txnDate;
    }

    public CashTransactions(Integer cashTransactionId, Integer accountId, String txnType, 
                            BigDecimal amount, LocalDate txnDate) {
        this.cashTransactionId = cashTransactionId;
        this.accountId = accountId;
        this.txnType = txnType;
        this.amount = amount;
        this.txnDate = txnDate;
    }

    // Getters and Setters
    public Integer getCashTransactionId() {
        return cashTransactionId;
    }

    public void setCashTransactionId(Integer cashTransactionId) {
        this.cashTransactionId = cashTransactionId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Accounts getAccount() {
        return account;
    }

    public void setAccount(Accounts account) {
        this.account = account;
    }

    public String getTxnType() {
        return txnType;
    }

    public void setTxnType(String txnType) {
        this.txnType = txnType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }
}
