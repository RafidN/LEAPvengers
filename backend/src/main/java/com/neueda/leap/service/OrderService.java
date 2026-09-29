package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.exception.TickerNotFoundException;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.Accounts;

import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.ForgotPasswordResponse;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchResult;

import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.LocalDate;
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final HoldingsRepository holdingsRepository;
    public OrderService(UserRepository userRepository, InstrumentsRepository instrumentsRepository, 
                        OrderRepository orderRepository, HoldingsRepository holdingsRepository) {
        this.userRepository = userRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.orderRepository = orderRepository;
        this.holdingsRepository = holdingsRepository;
    }


    @Transactional(readOnly = true)
    public List<OrderHistoryResult> placeOrder(Integer userId, OrderRequest request) 
            throws UserNotFoundException, InvalidInputException {
        
        // Validate user exists and get their clientId
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate input
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Order ticker cannot be empty");
        }
        if (request.getQuantity().compareTo(new BigDecimal(0)) <= 0)
        {
            throw new InvalidInputException("Order quantity must be greater than 0");
        }
        if (request.getOrderType() == null || request.getOrderType().trim() != "BUY" || request.getOrderType().trim() != "SELL") {
            throw new InvalidInputException("Order type must be BUY or SELL");
        }
        int clientId = user.getClientId();
        String ticker = request.getTicker().trim();
        BigDecimal quantity = request.getQuantity();
        String orderType = request.getOrderType().trim();
        Accounts account = request.getAccount();
        if(account.getClientId() != clientId){
            throw new InvalidCredentialsException("Specified client doesn't own this account");
        }
        // Execute parameterized query with user's clientId
        //
        OrderHistoryResult orderHistoryresult;
        List<PriceQuoteResult> prices = instrumentsRepository.searchInstrumentPrice(ticker);
        if(prices.size() == 0){
            throw new TickerNotFoundException("Ticker not found");
        }
        BigDecimal cost = quantity.multiply(prices.get(0).getCurrentPrice());
        if(account.getBalance().compareTo(cost) < 0){
            throw new InvalidInputException("Cost too high for current balance");
        }
        List<TickerSearchResult> holdingInventory = holdingsRepository.searchByInstrumentQuery(clientId, ticker);
        Long tickerStock = 0l;
        for(TickerSearchResult tickerSearchResult : holdingInventory){
            if(tickerSearchResult.getAccountId() == account.getAccountId()){
                tickerStock += tickerSearchResult.getQuantity();
            }
        }
        if(new BigDecimal(tickerStock).compareTo(quantity) < 0 && orderType.toUpperCase().equals("SELL")){
            throw new InvalidInputException("Not enough holding inventory in account to sell");
        }
        List<OrderHistoryResult> result = orderRepository.placeOrder(clientId, ticker, orderType, quantity, 
            prices.get(0).getCurrentPrice(), "ACCEPTED", LocalDate.now(), LocalDateTime.now());
        return result;
    }
}
