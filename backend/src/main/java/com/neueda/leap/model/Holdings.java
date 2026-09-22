package com.neueda.leap.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity representing holdings in the system.
 * Maps to the "holdings" table in the database.
 */
@Entity
@Table(name = "holdings", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "instrument_id"}))
public class Holdings {
    // Primary key for the holdings entity. GenerationType.IDENTITY works like serial in PostgreSQL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "holding_id")
    private Integer holdingId;

    // Foreign key to the accounts table.
    @Column(name = "account_id", nullable = false)
    private Integer accountId;

    // Foreign key to the instruments table.
    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    // Many-to-one relationship to the accounts entity.
    // Many instruments can belong to one account.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false, insertable = false, updatable = false)
    private Accounts account;

    // Many-to-one relationship to the instruments entity.
    // Many holdings can refer to the same instrument.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false, insertable = false, updatable = false)
    private Instruments instrument;

    //Precision at 14 with scale of 4 for the quantity column. Precision means the total number of digits, and scale means the number of digits to the right of the decimal point.
    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    // Constructors
    // Holdings left blank constructor for JPA. Required for entity instantiation.
    // Parameterized constructors for convenience when creating new Holdings instances.
    public Holdings() {
    }

    //This constructor refers to the holdings without specifying the holdingId, typically used when creating new holdings before they are persisted and assigned an ID.
    public Holdings(Integer accountId, Integer instrumentId, BigDecimal quantity, LocalDate asOfDate) {
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.asOfDate = asOfDate;
    }

    //This constructor includes the holdingId, typically used when the holding already exists in the database.
    public Holdings(Integer holdingId, Integer accountId, Integer instrumentId, 
                    BigDecimal quantity, LocalDate asOfDate) {
        this.holdingId = holdingId;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.asOfDate = asOfDate;
    }

    // Getters and Setters
    public Integer getHoldingId() {
        return holdingId;
    }

    public void setHoldingId(Integer holdingId) {
        this.holdingId = holdingId;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }
}
