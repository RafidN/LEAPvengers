package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.*;
import com.neueda.leap.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for historical data queries
 * Handles order history, cash transaction history, price history, and portfolio history
 * All time-period calculations are centralized here
 */
@Service
public class HistoryService {

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

    // ===== ORDER HISTORY METHODS =====

    /**
     * Get user's order history from past year
     * 
     * @param userId From JWT token
     * @return List of orders from past year
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrderHistoryPastYear(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return orderHistoryRepository.findOrdersPastYear(user.getClientId());
    }

    /**
     * Get user's order history from past month
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrderHistoryPastMonth(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return orderHistoryRepository.findOrdersPastMonth(user.getClientId());
    }

    /**
     * Get user's order history from past 7 days
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrderHistoryPast7Days(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return orderHistoryRepository.findOrdersPast7Days(user.getClientId());
    }

    /**
     * Get user's order history from past day
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrderHistoryPastDay(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return orderHistoryRepository.findOrdersPastDay(user.getClientId());
    }

    /**
     * Get user's order history from today only
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrderHistoryToday(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return orderHistoryRepository.findOrdersToday(user.getClientId());
    }

    // ===== CASH TRANSACTION HISTORY METHODS =====

    /**
     * Get user's cash transaction history from past year
     * 
     * @param userId From JWT token
     * @return List of cash transactions from past year
     */
    @Transactional(readOnly = true)
    public List<CashTransactionResult> getCashHistoryPastYear(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return cashTransactionHistoryRepository.findTransactionsPastYear(user.getClientId());
    }

    /**
     * Get user's cash transaction history from past month
     */
    @Transactional(readOnly = true)
    public List<CashTransactionResult> getCashHistoryPastMonth(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return cashTransactionHistoryRepository.findTransactionsPastMonth(user.getClientId());
    }

    /**
     * Get user's cash transaction history from past 7 days
     */
    @Transactional(readOnly = true)
    public List<CashTransactionResult> getCashHistoryPast7Days(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return cashTransactionHistoryRepository.findTransactionsPast7Days(user.getClientId());
    }

    /**
     * Get user's cash transaction history from past day
     */
    @Transactional(readOnly = true)
    public List<CashTransactionResult> getCashHistoryPastDay(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return cashTransactionHistoryRepository.findTransactionsPastDay(user.getClientId());
    }

    /**
     * Get user's cash transaction history from today only
     */
    @Transactional(readOnly = true)
    public List<CashTransactionResult> getCashHistoryToday(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return cashTransactionHistoryRepository.findTransactionsToday(user.getClientId());
    }

    // ===== PRICE HISTORY METHODS (PUBLIC) =====

    /**
     * Get price history from past year (no auth required)
     * 
     * @param ticker The ticker symbol
     * @return List of price quotes from past year
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryResult> getPriceHistoryPastYear(String ticker) 
            throws InvalidInputException {
        validateTicker(ticker);
        return priceHistoryRepository.findPricesPastYear(ticker);
    }

    /**
     * Get price history from past month
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryResult> getPriceHistoryPastMonth(String ticker) 
            throws InvalidInputException {
        validateTicker(ticker);
        return priceHistoryRepository.findPricesPastMonth(ticker);
    }

    /**
     * Get price history from past 7 days
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryResult> getPriceHistoryPast7Days(String ticker) 
            throws InvalidInputException {
        validateTicker(ticker);
        return priceHistoryRepository.findPricesPast7Days(ticker);
    }

    /**
     * Get price history from past day
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryResult> getPriceHistoryPastDay(String ticker) 
            throws InvalidInputException {
        validateTicker(ticker);
        return priceHistoryRepository.findPricesPastDay(ticker);
    }

    /**
     * Get price history from today only
     */
    @Transactional(readOnly = true)
    public List<PriceHistoryResult> getPriceHistoryToday(String ticker) 
            throws InvalidInputException {
        validateTicker(ticker);
        return priceHistoryRepository.findPricesToday(ticker);
    }

    // ===== PORTFOLIO HISTORY METHODS =====

    /**
     * Get portfolio holdings from past year
     * 
     * @param userId From JWT token
     * @return List of portfolio snapshots from past year
     */
    @Transactional(readOnly = true)
    public List<PortfolioHistoryResult> getPortfolioHistoryPastYear(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return portfolioHistoryRepository.findPortfolioPastYear(user.getClientId());
    }

    /**
     * Get portfolio holdings from past month
     */
    @Transactional(readOnly = true)
    public List<PortfolioHistoryResult> getPortfolioHistoryPastMonth(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return portfolioHistoryRepository.findPortfolioPastMonth(user.getClientId());
    }

    /**
     * Get portfolio holdings from past 7 days
     */
    @Transactional(readOnly = true)
    public List<PortfolioHistoryResult> getPortfolioHistoryPast7Days(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return portfolioHistoryRepository.findPortfolioPast7Days(user.getClientId());
    }

    /**
     * Get portfolio holdings from past day
     */
    @Transactional(readOnly = true)
    public List<PortfolioHistoryResult> getPortfolioHistoryPastDay(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return portfolioHistoryRepository.findPortfolioPastDay(user.getClientId());
    }

    /**
     * Get portfolio holdings from today only
     */
    @Transactional(readOnly = true)
    public List<PortfolioHistoryResult> getPortfolioHistoryToday(Integer userId) 
            throws UserNotFoundException {
        Users user = validateUser(userId);
        return portfolioHistoryRepository.findPortfolioToday(user.getClientId());
    }

    // ===== HELPER METHODS =====

    /**
     * Validate user exists and return user object
     */
    private Users validateUser(Integer userId) throws UserNotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    /**
     * Validate ticker input
     */
    private void validateTicker(String ticker) throws InvalidInputException {
        if (ticker == null || ticker.trim().isEmpty()) {
            throw new InvalidInputException("Ticker cannot be empty");
        }

        String trimmed = ticker.trim();
        if (trimmed.length() >= 6) {
            throw new InvalidInputException("Ticker too long");
        }
    }
}
