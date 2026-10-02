package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.util.List;



public class AccountPortfolioResponse {

    private Integer accountId;
    private BigDecimal balance;
    private List<AccountHoldingResponse> holdings;
    private BigDecimal totalBalance;



    public AccountPortfolioResponse(Integer accountId, BigDecimal balance, List<AccountHoldingResponse> holdings, BigDecimal totalBalance) {
        this.accountId = accountId;
        this.balance = balance;
        this.holdings = holdings;
        this.totalBalance = totalBalance;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }


    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public List<AccountHoldingResponse> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<AccountHoldingResponse> holdings) {
        this.holdings = holdings;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }
}  

