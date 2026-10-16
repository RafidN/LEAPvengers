package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.dto.QuoteResponse;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InstrumentService business logic
 */
@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    private InstrumentService instrumentService;
    @Mock
    private InstrumentsRepository instrumentsRepository;
    @Mock
    private LatestPriceQuotesRepository priceQuotesRepository;

    @BeforeEach
    void setUp() {
        // Thursday 14:00 UTC: New York is open (10:00), India is closed (19:30)
        Clock clock = Clock.fixed(Instant.parse("2026-10-08T14:00:00Z"), ZoneId.of("UTC"));
        instrumentService = new InstrumentService(instrumentsRepository, priceQuotesRepository,
                new TradingRules(clock, Duration.ofMinutes(1)), new MarketHours(clock, true));
    }

    @Test
    void getInstruments_ReturnsAllInstruments() {
        List<Instruments> instruments = List.of(
            new Instruments(1, "AAPL", "Apple Inc.", "EQUITY", "NASDAQ"),
            new Instruments(2, "BTC", "Bitcoin", "CRYPTO", "SPOT")
        );
        when(instrumentsRepository.findAll()).thenReturn(instruments);

        List<Instruments> result = instrumentService.getInstruments();

        assertEquals(2, result.size());
        assertEquals("AAPL", result.get(0).getTicker());
        assertEquals("BTC", result.get(1).getTicker());
    }

    @Test
    void getQuoteByInstrumentId_WithValidId_ReturnsQuote() {
        LatestPriceQuotes priceQuote = new LatestPriceQuotes();
        priceQuote.setInstrumentId(1);
        priceQuote.setPrice(new BigDecimal("150.25"));
        priceQuote.setQuoteTimestamp(LocalDateTime.of(2026, 10, 1, 16, 30, 0));
        when(priceQuotesRepository.findByInstrumentId(1)).thenReturn(Optional.of(priceQuote));

        QuoteResponse result = instrumentService.getQuoteByInstrumentId(1);

        assertEquals(new BigDecimal("150.25"), result.getPrice());
        assertEquals(LocalDateTime.of(2026, 10, 1, 16, 30, 0), result.getTimestamp());
    }

    @Test
    void getQuoteByInstrumentId_WithNonExistentId_ThrowsUserNotFoundException() {
        when(priceQuotesRepository.findByInstrumentId(999)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> 
            instrumentService.getQuoteByInstrumentId(999)
        );
    }

    private void givenQuote(String source) {
        LatestPriceQuotes priceQuote = new LatestPriceQuotes();
        priceQuote.setInstrumentId(1);
        priceQuote.setPrice(new BigDecimal("72.74"));
        priceQuote.setQuoteTimestamp(LocalDateTime.of(2026, 10, 8, 13, 59, 58));
        priceQuote.setSource(source);
        when(priceQuotesRepository.findByInstrumentId(1)).thenReturn(Optional.of(priceQuote));
    }

    @Test
    void quoteSaysWhereThePriceCameFromAndThatItCanBeTraded() {
        givenQuote("YAHOO_LIVE");
        when(instrumentsRepository.findById(1)).thenReturn(Optional.of(new Instruments(1, "NFLX", "Netflix", "Equity", "US")));

        QuoteResponse result = instrumentService.getQuoteByInstrumentId(1);

        assertEquals("YAHOO_LIVE", result.getSource());
        assertTrue(result.isTradable());
        assertTrue(result.isMarketOpen());
        assertEquals("the New York stock market", result.getMarket());
        assertEquals("2026-10-08T16:00-04:00", result.getClosesAt().toString());
        assertNull(result.getNotTradableReason());
    }

    @Test
    void quoteForAClosedMarketSaysWhyAndWhenItOpens() {
        givenQuote("YAHOO_LIVE");
        when(instrumentsRepository.findById(1)).thenReturn(Optional.of(new Instruments(1, "TCS.NS", "Tata Consultancy", "Equity", "IN")));

        QuoteResponse result = instrumentService.getQuoteByInstrumentId(1);

        assertFalse(result.isTradable());
        assertFalse(result.isMarketOpen());
        assertEquals("2026-10-09T09:15+05:30", result.getOpensAt().toString());
        assertEquals("TCS.NS can't be traded right now. The National Stock Exchange of India is closed. "
                + "It opens at 09:15 on Fri 9 Oct (India time).", result.getNotTradableReason());
    }

    @Test
    void quoteForASuspendedInstrumentIsNotTradableEvenWhileItsMarketIsOpen() {
        givenQuote("YAHOO_LIVE");
        Instruments suspended = new Instruments(1, "NFLX", "Netflix", "Equity", "US");
        suspended.setTradable(false);
        when(instrumentsRepository.findById(1)).thenReturn(Optional.of(suspended));

        QuoteResponse result = instrumentService.getQuoteByInstrumentId(1);

        assertTrue(result.isMarketOpen());
        assertFalse(result.isTradable());
        assertTrue(result.getNotTradableReason().contains("suspended"));
    }
}
