package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "latest_price_quotes")
public class LatestPriceQuotes {
    @Id
    @Column(name = "instrument_id")
    private Integer instrumentId;

    @Column(name = "price", nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "quote_timestamp", nullable = false)
    private LocalDateTime quoteTimestamp;

    // YAHOO_LIVE, YAHOO_HISTORY or SIMULATED (see price_quotes.source)
    @Column(name = "source")
    private String source;

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getQuoteTimestamp() {
        return quoteTimestamp;
    }

    public void setQuoteTimestamp(LocalDateTime quoteTimestamp) {
        this.quoteTimestamp = quoteTimestamp;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}