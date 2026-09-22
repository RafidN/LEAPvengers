package com.neueda.leap.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for instrument search input.
 */
public class InstrumentSearchRequest {
    @JsonProperty("query")
    @JsonAlias("ticker")
    private String query;

    public InstrumentSearchRequest() {
    }

    public InstrumentSearchRequest(String query) {
        this.query = query;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}