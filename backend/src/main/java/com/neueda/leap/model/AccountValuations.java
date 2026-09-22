package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "account_valuations")
public class AccountValuations {
    @Id
    @Column(name = "account_id")
    private Integer accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Accounts account;

    @Column(name = "total_value", nullable = false, precision = 14, scale = 4)
    private BigDecimal totalValue;

    public AccountValuations() {
    }

    public AccountValuations(Integer accountId, BigDecimal totalValue) {
        this.accountId = accountId;
        this.totalValue = totalValue;
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

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }
}