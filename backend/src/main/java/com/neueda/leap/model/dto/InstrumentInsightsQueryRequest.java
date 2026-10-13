package com.neueda.leap.model.dto;

/**
 * Request DTO for instrument-level analytics on the Insights page.
 */
public class InstrumentInsightsQueryRequest {
    private String segment;
    private Integer lookbackDays;
    private Integer limit;

    public InstrumentInsightsQueryRequest() {
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

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}