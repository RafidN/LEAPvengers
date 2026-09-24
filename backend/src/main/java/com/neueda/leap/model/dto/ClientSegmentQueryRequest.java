package com.neueda.leap.model.dto;

import java.math.BigDecimal;

/**
 * Request DTO for querying client segments.
 */
public class ClientSegmentQueryRequest {
    private String segment;
    private Integer lookbackDays;
    private BigDecimal dormantMaxPortfolioValue;
    private Integer dormantMaxOrderCount;
    private BigDecimal premierMinPortfolioValue;
    private Integer activeMinOrderCount;

    public ClientSegmentQueryRequest() {
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public Integer getLookbackDays() {
        return lookbackDays;
    }

    public void setLookbackDays(Integer lookbackDays) {
        this.lookbackDays = lookbackDays;
    }

    public BigDecimal getDormantMaxPortfolioValue() {
        return dormantMaxPortfolioValue;
    }

    public void setDormantMaxPortfolioValue(BigDecimal dormantMaxPortfolioValue) {
        this.dormantMaxPortfolioValue = dormantMaxPortfolioValue;
    }

    public Integer getDormantMaxOrderCount() {
        return dormantMaxOrderCount;
    }

    public void setDormantMaxOrderCount(Integer dormantMaxOrderCount) {
        this.dormantMaxOrderCount = dormantMaxOrderCount;
    }

    public BigDecimal getPremierMinPortfolioValue() {
        return premierMinPortfolioValue;
    }

    public void setPremierMinPortfolioValue(BigDecimal premierMinPortfolioValue) {
        this.premierMinPortfolioValue = premierMinPortfolioValue;
    }

    public Integer getActiveMinOrderCount() {
        return activeMinOrderCount;
    }

    public void setActiveMinOrderCount(Integer activeMinOrderCount) {
        this.activeMinOrderCount = activeMinOrderCount;
    }
}