package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;



public class AccountHoldingResponse {

    @NotBlank
    private String ticker;

    @NotBlank
    private String instrumentName;

    @NotBlank
    private String assetClass;

    @NotNull @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal quantity;

    @NotNull @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal latestPrice;

    @NotNull
    private BigDecimal value;

    @NotNull
    private LocalDateTime priceTimestamp;

    public AccountHoldingResponse(String ticker, String instrumentName, String assetClass, BigDecimal quantity, BigDecimal latestPrice, BigDecimal value, LocalDateTime priceTimestamp) {
        this.ticker = ticker;
        this.instrumentName = instrumentName;
        this.assetClass = assetClass;
        this.quantity = quantity;
        this.latestPrice = latestPrice;
        this.value = value;
        this.priceTimestamp = priceTimestamp;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }


    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
            this.latestPrice = latestPrice;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        if (value == null ) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        else {
            this.value = value;
        }
    }

    public LocalDateTime getPriceTimestamp() {
        return priceTimestamp;
    }

    public void setPriceTimestamp(LocalDateTime priceTimestamp) {
        this.priceTimestamp = priceTimestamp;
    }

}  

