package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.InstrumentSearchRequest;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchResult;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for instrument search with validation.
 */
@Service
public class InstrumentSearchService {

    private static final int MAX_QUERY_LENGTH = 100;

    private final HoldingsRepository holdingsRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;

    /**
     * Constructor for InstrumentSearchService.
     *
     * @param holdingsRepository Repository for holdings search data
     * @param userRepository Repository for user data
     * @param instrumentsRepository Repository for public instrument price quote data
     */
    public InstrumentSearchService(HoldingsRepository holdingsRepository, UserRepository userRepository,
                                   InstrumentsRepository instrumentsRepository) {
        this.holdingsRepository = holdingsRepository;
        this.userRepository = userRepository;
        this.instrumentsRepository = instrumentsRepository;
    }

    /**
     * Search holdings by instrument query.
     *
     * @param userId From JWT token
     * @param request Search request with ticker symbol or instrument name
     * @return List of matching holdings
     */
    @Transactional(readOnly = true)
    public List<TickerSearchResult> searchHoldings(Integer userId, InstrumentSearchRequest request)
            throws UserNotFoundException, InvalidInputException {

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        String query = validateQuery(request);

        return holdingsRepository.searchByInstrumentQuery(user.getClientId(), query);
    }

    /**
     * Search public market price quotes by instrument query.
     *
     * @param request Search request with ticker symbol or instrument name
     * @return List of matching price quotes
     */
    @Transactional(readOnly = true)
    public List<PriceQuoteResult> searchInstrumentPrice(InstrumentSearchRequest request)
            throws InvalidInputException {

        String query = validateQuery(request);

        return instrumentsRepository.searchInstrumentPrice(query);
    }

    private String validateQuery(InstrumentSearchRequest request) throws InvalidInputException {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new InvalidInputException("Search query cannot be empty");
        }

        String query = request.getQuery().trim();

        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidInputException("Search query too long");
        }

        return query;
    }
}