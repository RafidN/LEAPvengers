package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for latest price quotes
 * Contains the current price and timestamp for an instrument
 */
public class QuoteResponse {
    private BigDecimal price;
    private LocalDateTime timestamp;

    public QuoteResponse() {
    }

    public QuoteResponse(BigDecimal price, LocalDateTime timestamp) {
        this.price = price;
        this.timestamp = timestamp;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
