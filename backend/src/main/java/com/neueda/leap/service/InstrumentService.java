package com.neueda.leap.service;

import com.neueda.leap.exception.TradingRuleException;
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
    private final TradingRules tradingRules;
    private final MarketHours marketHours;

    public InstrumentService(InstrumentsRepository instrumentsRepository, 
                            LatestPriceQuotesRepository latestPriceQuotesRepository,
                            TradingRules tradingRules, MarketHours marketHours) {
        this.instrumentsRepository = instrumentsRepository;
        this.latestPriceQuotesRepository = latestPriceQuotesRepository;
        this.tradingRules = tradingRules;
        this.marketHours = marketHours;
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
        
        QuoteResponse response = new QuoteResponse(quote.getPrice(), quote.getQuoteTimestamp());
        response.setSource(quote.getSource());
        instrumentsRepository.findById(instrumentId).ifPresent(instrument -> describeTradability(instrument, response));
        return response;
    }

    /** Whether an order would get past the tradability rule right now, using the same rule the order goes through. */
    private void describeTradability(Instruments instrument, QuoteResponse response) {
        MarketHours.Status market = marketHours.statusOf(instrument);
        response.setDelayMinutes(instrument.getQuoteDelayMinutes());
        response.setMarketOpen(market.open());
        response.setMarket(market.venue());
        response.setOpensAt(market.opensAt() == null ? null : market.opensAt().toOffsetDateTime());
        response.setClosesAt(market.closesAt() == null ? null : market.closesAt().toOffsetDateTime());
        try {
            tradingRules.checkTradable(instrument, market);
            response.setTradable(true);
        } catch (TradingRuleException e) {
            response.setTradable(false);
            response.setNotTradableReason(e.getMessage());
        }
    }

}
