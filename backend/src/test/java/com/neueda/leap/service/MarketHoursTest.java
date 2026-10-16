package com.neueda.leap.service;

import com.neueda.leap.model.Instruments;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BR-05: which markets are open when, in each exchange's own time zone. */
class MarketHoursTest {

    private static final Instruments NFLX = new Instruments("NFLX", "Netflix", "Equity", "US");
    private static final Instruments SHELL = new Instruments("SHEL.L", "Shell plc (London)", "Equity", "UK");
    private static final Instruments TCS = new Instruments("TCS.NS", "Tata Consultancy", "Equity", "IN");
    private static final Instruments EURUSD = new Instruments("EURUSD=X", "Euro / US Dollar", "FX", "US");
    private static final Instruments BTC = new Instruments("BTC-USD", "Bitcoin", "Crypto", "US");

    private static MarketHours at(String instant) {
        return new MarketHours(Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")), true);
    }

    @Test
    void newYorkIsOpenFromHalfNineToFourNewYorkTime() {
        // October is daylight saving time in New York (UTC-4)
        assertFalse(at("2026-10-15T13:29:00Z").statusOf(NFLX).open());
        assertTrue(at("2026-10-15T13:30:00Z").statusOf(NFLX).open());
        assertTrue(at("2026-10-15T19:59:00Z").statusOf(NFLX).open());
        assertFalse(at("2026-10-15T20:00:00Z").statusOf(NFLX).open());
    }

    @Test
    void openMarketSaysWhenItCloses() {
        MarketHours.Status status = at("2026-10-15T18:25:00Z").statusOf(NFLX);

        assertEquals("2026-10-15T16:00-04:00[America/New_York]", status.closesAt().toString());
        assertNull(status.closedReason());
    }

    @Test
    void londonFollowsLondonTimeIncludingTheClockChange() {
        // Before the clocks go back on 25 Oct London is UTC+1, after it is UTC+0
        assertTrue(at("2026-10-23T07:00:00Z").statusOf(SHELL).open());
        assertFalse(at("2026-10-26T07:00:00Z").statusOf(SHELL).open());
        assertTrue(at("2026-10-26T08:00:00Z").statusOf(SHELL).open());
        assertFalse(at("2026-10-26T16:30:00Z").statusOf(SHELL).open());
    }

    @Test
    void indiaIsOpenFromQuarterPastNineToHalfThreeIndiaTime() {
        // India is UTC+5:30 all year
        assertFalse(at("2026-10-15T03:44:00Z").statusOf(TCS).open());
        assertTrue(at("2026-10-15T03:45:00Z").statusOf(TCS).open());
        assertFalse(at("2026-10-15T10:00:00Z").statusOf(TCS).open());
    }

    @Test
    void closedMarketAfterFridaysCloseOpensOnMonday() {
        MarketHours.Status status = at("2026-10-16T21:00:00Z").statusOf(NFLX);

        assertFalse(status.open());
        assertEquals("2026-10-19T09:30-04:00[America/New_York]", status.opensAt().toString());
        assertEquals("The New York stock market is closed. It opens at 09:30 on Mon 19 Oct (New York time).",
                status.closedReason());
    }

    @Test
    void closedMarketBeforeTodaysOpenOpensLaterToday() {
        assertEquals("2026-10-15T09:30-04:00[America/New_York]",
                at("2026-10-15T12:00:00Z").statusOf(NFLX).opensAt().toString());
    }

    @Test
    void stocksDoNotTradeAtTheWeekend() {
        assertFalse(at("2026-10-17T15:00:00Z").statusOf(NFLX).open());
        assertFalse(at("2026-10-17T10:00:00Z").statusOf(SHELL).open());
        assertFalse(at("2026-10-18T05:00:00Z").statusOf(TCS).open());
    }

    @Test
    void fxTradesFromSundayEveningToFridayEveningNewYorkTime() {
        // Sunday 17:00 New York is 21:00 UTC in October
        assertFalse(at("2026-10-18T20:59:00Z").statusOf(EURUSD).open());
        assertTrue(at("2026-10-18T21:00:00Z").statusOf(EURUSD).open());
        assertTrue(at("2026-10-21T03:00:00Z").statusOf(EURUSD).open());
        assertTrue(at("2026-10-23T20:59:00Z").statusOf(EURUSD).open());
        assertFalse(at("2026-10-23T21:00:00Z").statusOf(EURUSD).open());
        assertFalse(at("2026-10-24T12:00:00Z").statusOf(EURUSD).open());
    }

    @Test
    void fxClosedForTheWeekendSaysWhenItReopens() {
        MarketHours.Status status = at("2026-10-24T12:00:00Z").statusOf(EURUSD);

        assertEquals("2026-10-25T17:00-04:00[America/New_York]", status.opensAt().toString());
        assertTrue(status.closedReason().startsWith("The foreign exchange market is closed for the weekend."));
    }

    @Test
    void cryptoNeverCloses() {
        assertTrue(at("2026-10-17T03:00:00Z").statusOf(BTC).open());
        assertNull(at("2026-10-17T03:00:00Z").statusOf(BTC).closesAt());
    }

    @Test
    void notEnforcedEverythingIsOpen() {
        MarketHours anyTime = new MarketHours(Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneId.of("UTC")), false);

        assertTrue(anyTime.statusOf(NFLX).open());
        assertTrue(anyTime.statusOf(EURUSD).open());
    }
}
