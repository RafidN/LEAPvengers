package com.neueda.leap.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "accounts")
public class Accounts {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Integer accountId;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, insertable = false, updatable = false)
    private Clients client;

    @Column(name = "opened_date", nullable = false)
    private LocalDate openedDate;

    @Column(name = "balance", nullable = false, precision = 14, scale = 4)
    private BigDecimal balance;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Holdings> holdings;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Orders> orders;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CashTransactions> cashTransactions;

    // Constructors
    public Accounts() {
    }

    public Accounts(Integer clientId, LocalDate openedDate, BigDecimal balance) {
        this.clientId = clientId;
        this.openedDate = openedDate;
        this.balance = balance;
    }

    public Accounts(Integer accountId, Integer clientId, LocalDate openedDate, BigDecimal balance) {
        this.accountId = accountId;
        this.clientId = clientId;
        this.openedDate = openedDate;
        this.balance = balance;
    }

    // Getters and Setters
    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public Clients getClient() {
        return client;
    }

    public void setClient(Clients client) {
        this.client = client;
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

    public List<Holdings> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<Holdings> holdings) {
        this.holdings = holdings;
    }

    public List<Orders> getOrders() {
        return orders;
    }

    public void setOrders(List<Orders> orders) {
        this.orders = orders;
    }

    public List<CashTransactions> getCashTransactions() {
        return cashTransactions;
    }

    public void setCashTransactions(List<CashTransactions> cashTransactions) {
        this.cashTransactions = cashTransactions;
    }

}
