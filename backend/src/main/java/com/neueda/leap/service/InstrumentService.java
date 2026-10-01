package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.dto.QuoteResponse;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for managing instruments and their price quotes
 */
@Service
public class InstrumentService {

    private final InstrumentsRepository instrumentsRepository;
    private final LatestPriceQuotesRepository latestPriceQuotesRepository;

    public InstrumentService(InstrumentsRepository instrumentsRepository, 
                            LatestPriceQuotesRepository latestPriceQuotesRepository) {
        this.instrumentsRepository = instrumentsRepository;
        this.latestPriceQuotesRepository = latestPriceQuotesRepository;
    }

    /**
     * Get all tradable instruments with their asset class and market
     * 
     * @return List of all instruments
     */
    public List<Instruments> getInstruments() {
        return instrumentsRepository.findAll();
    }

    /**
     * Get the latest price quote for a specific instrument
     * 
     * @param instrumentId The instrument ID
     * @return Quote response with price and timestamp
     * @throws UserNotFoundException if no quote found for the instrument
     */
    public QuoteResponse getQuoteByInstrumentId(Integer instrumentId) {
        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(instrumentId)
            .orElseThrow(() -> new UserNotFoundException("No quote found for instrument ID: " + instrumentId));
        
        return new QuoteResponse(quote.getPrice(), quote.getQuoteTimestamp());
    }

}
