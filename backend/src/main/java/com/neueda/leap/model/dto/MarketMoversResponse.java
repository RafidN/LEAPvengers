package com.neueda.leap.model.dto;

import java.time.Instant;
import java.util.List;

/** Today's top gainers and losers, with when they were read from the source. */
public class MarketMoversResponse {
    private List<MarketMover> gainers;
    private List<MarketMover> losers;
    private Instant asOf;
    private String source;

    public MarketMoversResponse() {
    }

    public MarketMoversResponse(List<MarketMover> gainers, List<MarketMover> losers, Instant asOf, String source) {
        this.gainers = gainers;
        this.losers = losers;
        this.asOf = asOf;
        this.source = source;
    }

    public List<MarketMover> getGainers() {
        return gainers;
    }

    public void setGainers(List<MarketMover> gainers) {
        this.gainers = gainers;
    }

    public List<MarketMover> getLosers() {
        return losers;
    }

    public void setLosers(List<MarketMover> losers) {
        this.losers = losers;
    }

    public Instant getAsOf() {
        return asOf;
    }

    public void setAsOf(Instant asOf) {
        this.asOf = asOf;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
