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
import java.time.LocalDateTime;
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
        instrumentService = new InstrumentService(instrumentsRepository, priceQuotesRepository);
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
}

