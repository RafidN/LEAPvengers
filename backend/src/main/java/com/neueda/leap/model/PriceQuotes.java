package com.neueda.leap.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "price_quotes", uniqueConstraints = @UniqueConstraint(columnNames = {"instrument_id", "quote_timestamp"}))
public class PriceQuotes {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_quote_id")
    private Integer priceQuoteId;

    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false, insertable = false, updatable = false)
    private Instruments instrument;

    @Column(name = "price", nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "volume")
    private Long volume;

    @Column(name = "quote_timestamp", nullable = false)
    private LocalDateTime quoteTimestamp;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    // Constructors
    public PriceQuotes() {
    }

    public PriceQuotes(Integer instrumentId, BigDecimal price, Long volume, 
                       LocalDateTime quoteTimestamp) {
        this.instrumentId = instrumentId;
        this.price = price;
        this.volume = volume;
        this.quoteTimestamp = quoteTimestamp;
        this.fetchedAt = LocalDateTime.now();
    }

    public PriceQuotes(Integer priceQuoteId, Integer instrumentId, BigDecimal price, Long volume, 
                       LocalDateTime quoteTimestamp, LocalDateTime fetchedAt) {
        this.priceQuoteId = priceQuoteId;
        this.instrumentId = instrumentId;
        this.price = price;
        this.volume = volume;
        this.quoteTimestamp = quoteTimestamp;
        this.fetchedAt = fetchedAt;
    }

    // Getters and Setters
    public Integer getPriceQuoteId() {
        return priceQuoteId;
    }

    public void setPriceQuoteId(Integer priceQuoteId) {
        this.priceQuoteId = priceQuoteId;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public Instruments getInstrument() {
        return instrument;
    }

    public void setInstrument(Instruments instrument) {
        this.instrument = instrument;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public LocalDateTime getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public void setQuoteTimestamp(LocalDateTime quoteTimestamp) {
        this.quoteTimestamp = quoteTimestamp;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
