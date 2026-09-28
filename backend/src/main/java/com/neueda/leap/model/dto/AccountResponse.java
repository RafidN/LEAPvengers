package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AccountResponse {
    private Integer accountId;
    private LocalDate openedDate;
    private BigDecimal balance;


    public AccountResponse(Integer accountId, LocalDate openedDate, BigDecimal balance) {
        this.accountId = accountId;
        this.openedDate = openedDate;
        this.balance = balance;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public LocalDate getOpenedDate() {
        return openedDate;
    }

    public void setOpenedDate(LocalDate openedDate) {
        this.openedDate = openedDate;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}