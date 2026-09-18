package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchRequest;
import com.neueda.leap.model.dto.TickerSearchResult;
import com.neueda.leap.repository.PriceQuoteRepository;
import com.neueda.leap.repository.TickerRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for ticker search with validation
 */
@Service
public class TickerSearchService {

    private final TickerRepository tickerRepository;
    private final UserRepository userRepository;
    private final PriceQuoteRepository priceQuoteRepository;

    /**
     * Constructor for TickerSearchService
     * 
     * @param tickerRepository Repository for ticker data
     * @param userRepository Repository for user data
     * @param priceQuoteRepository Repository for public price quote data
     */
    public TickerSearchService(TickerRepository tickerRepository, UserRepository userRepository,
                               PriceQuoteRepository priceQuoteRepository) {
        this.tickerRepository = tickerRepository;
        this.userRepository = userRepository;
        this.priceQuoteRepository = priceQuoteRepository;
    }

    /**
     * Search holdings by ticker
     * 
     * @param userId From JWT token
     * @param request Search request with ticker
     * @return List of matching holdings
     */

    // From here on out this is a transactional read-only method for searching tickers
    // If userId or request is invalid, appropriate exceptions will be thrown
    @Transactional(readOnly = true)
    public List<TickerSearchResult> searchByTicker(Integer userId, TickerSearchRequest request) 
            throws UserNotFoundException, InvalidInputException {
        
        // Validate user exists and get their clientId
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate input
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Ticker cannot be empty");
        }

        String ticker = request.getTicker().trim();
        
        if (ticker.trim().length() >= 6) {
            throw new InvalidInputException("Ticker too long");
        }

        // Execute parameterized query with user's clientId
        return tickerRepository.searchByTicker(user.getClientId(), ticker);
    }

    /**
     * Search ticker price quotes (public market data)
     * No authentication required - returns current market price for any ticker
     * 
     * @param request Search request with ticker
     * @return List of matching price quotes
     */
    @Transactional(readOnly = true)
    public List<PriceQuoteResult> searchTickerPrice(TickerSearchRequest request) 
            throws InvalidInputException {
        
        // Validate input
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Ticker cannot be empty");
        }

        String ticker = request.getTicker().trim();
        
        if (ticker.length() >= 6) {
            throw new InvalidInputException("Ticker too long");
        }

        // Execute parameterized query - no clientId filtering needed (public data)
        return priceQuoteRepository.searchTickerPrice(ticker);
    }
}
