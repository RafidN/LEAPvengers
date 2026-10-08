package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.CashTransactionResult;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.PortfolioHistoryResult;
import com.neueda.leap.model.dto.PriceHistoryResult;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.PortfolioRepository;
import com.neueda.leap.repository.PriceQuotesRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for historical data queries.
 * Periods are selected with 1d, 7d, 1m, or 1y. Omitting the period returns all data up to now.
 */
@Service
public class HistoryService {
    private static final String PERIOD_1D = "1d";
    private static final String PERIOD_7D = "7d";
    private static final String PERIOD_1M = "1m";
    private static final String PERIOD_1Y = "1y";

    private final OrderRepository orderHistoryRepository;
    private final CashTransactionRepository cashTransactionHistoryRepository;
    private final PriceQuotesRepository priceHistoryRepository;
    private final PortfolioRepository portfolioHistoryRepository;
    private final UserRepository userRepository;

    public HistoryService(OrderRepository orderHistoryRepository,
            CashTransactionRepository cashTransactionHistoryRepository,
            PriceQuotesRepository priceHistoryRepository,
            PortfolioRepository portfolioHistoryRepository,
            UserRepository userRepository) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.cashTransactionHistoryRepository = cashTransactionHistoryRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.portfolioHistoryRepository = portfolioHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrderHistory(Integer userId, String period)
            throws UserNotFoundException, InvalidInputException {
        Users user = validateUser(userId);
        return switch (normalizePeriod(period)) {
            case null -> orderHistoryRepository.findAllOrderHistory(user.getClientId());
            case PERIOD_1D -> orderHistoryRepository.findOrdersPastDay(user.getClientId());
            case PERIOD_7D -> orderHistoryRepository.findOrdersPast7Days(user.getClientId());
            case PERIOD_1M -> orderHistoryRepository.findOrdersPastMonth(user.getClientId());
            case PERIOD_1Y -> orderHistoryRepository.findOrdersPastYear(user.getClientId());
            default -> throw new InvalidInputException("Unsupported period: " + period);
        };
    }

    @Transactional(readOnly = true)
    public List<CashTransactionResult> getCashHistory(Integer userId, String period)
            throws UserNotFoundException, InvalidInputException {
        Users user = validateUser(userId);
        return switch (normalizePeriod(period)) {
            case null -> cashTransactionHistoryRepository.findAllTransactionHistory(user.getClientId());
            case PERIOD_1D -> cashTransactionHistoryRepository.findTransactionsPastDay(user.getClientId());
            case PERIOD_7D -> cashTransactionHistoryRepository.findTransactionsPast7Days(user.getClientId());
            case PERIOD_1M -> cashTransactionHistoryRepository.findTransactionsPastMonth(user.getClientId());
            case PERIOD_1Y -> cashTransactionHistoryRepository.findTransactionsPastYear(user.getClientId());
            default -> throw new InvalidInputException("Unsupported period: " + period);
        };
    }

    @Transactional(readOnly = true)
    public List<PriceHistoryResult> getPriceHistory(String query, String period)
            throws InvalidInputException {
        validateInstrumentQuery(query);
        return switch (normalizePeriod(period)) {
            case null -> priceHistoryRepository.findAllPriceHistory(query);
            case PERIOD_1D -> priceHistoryRepository.findPricesPastDay(query);
            case PERIOD_7D -> priceHistoryRepository.findPricesPast7Days(query);
            case PERIOD_1M -> priceHistoryRepository.findPricesPastMonth(query);
            case PERIOD_1Y -> priceHistoryRepository.findPricesPastYear(query);
            default -> throw new InvalidInputException("Unsupported period: " + period);
        };
    }

    @Transactional(readOnly = true)
    public List<PortfolioHistoryResult> getPortfolioHistory(Integer userId, String period)
            throws UserNotFoundException, InvalidInputException {
        Users user = validateUser(userId);
        return switch (normalizePeriod(period)) {
            case null -> portfolioHistoryRepository.findAllPortfolioHistory(user.getClientId());
            case PERIOD_1D -> portfolioHistoryRepository.findPortfolioPastDay(user.getClientId());
            case PERIOD_7D -> portfolioHistoryRepository.findPortfolioPast7Days(user.getClientId());
            case PERIOD_1M -> portfolioHistoryRepository.findPortfolioPastMonth(user.getClientId());
            case PERIOD_1Y -> portfolioHistoryRepository.findPortfolioPastYear(user.getClientId());
            default -> throw new InvalidInputException("Unsupported period: " + period);
        };
    }

    private Users validateUser(Integer userId) throws UserNotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private String normalizePeriod(String period) throws InvalidInputException {
        if (period == null || period.isBlank()) {
            return null;
        }

        String normalized = period.trim().toLowerCase();
        return switch (normalized) {
            case PERIOD_1D, PERIOD_7D, PERIOD_1M, PERIOD_1Y -> normalized;
            default -> throw new InvalidInputException("Unsupported period: " + period);
        };
    }

    private void validateInstrumentQuery(String query) throws InvalidInputException {
        if (query == null || query.trim().isEmpty()) {
            throw new InvalidInputException("Search query cannot be empty");
        }

        String trimmed = query.trim();
        if (trimmed.length() > 100) {
            throw new InvalidInputException("Search query too long");
        }
    }
}
