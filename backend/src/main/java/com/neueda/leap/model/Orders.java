package com.neueda.leap.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Integer orderId;

    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Accounts account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false, insertable = false, updatable = false)
    private Instruments instrument;

    @Column(name = "order_type", nullable = false)
    private String orderType;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "order_status", nullable = false)
    private String orderStatus;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    // Constructors
    public Orders() {
    }

    public Orders(Integer accountId, Integer instrumentId, String orderType, BigDecimal quantity, 
                  BigDecimal price, LocalDate orderDate) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.orderDate = orderDate;
        this.orderStatus = "Pending";
        this.submittedAt = LocalDateTime.now();
    }

    public Orders(Integer orderId, Integer accountId, Integer instrumentId, String orderType, 
                  BigDecimal quantity, BigDecimal price, LocalDate orderDate, String orderStatus, 
                  LocalDateTime submittedAt, LocalDateTime executedAt) {
        this.orderId = orderId;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.orderDate = orderDate;
        this.orderStatus = orderStatus;
        this.submittedAt = submittedAt;
        this.executedAt = executedAt;
    }

    // Getters and Setters
    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Accounts getAccount() {
        return account;
    }

    public void setAccount(Accounts account) {
        this.account = account;
    }

    public Instruments getInstrument() {
        return instrument;
    }

    public void setInstrument(Instruments instrument) {
        this.instrument = instrument;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }
}
