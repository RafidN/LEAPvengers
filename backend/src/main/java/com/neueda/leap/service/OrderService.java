package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.exception.TickerNotFoundException;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Orders;

import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.ForgotPasswordResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchResult;
import com.neueda.leap.model.dto.InstrumentIdResult;

import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.AccountRepository;

import com.neueda.leap.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.LocalDate;
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final HoldingsRepository holdingsRepository;
    private final AccountRepository accountRepository;
    public OrderService(UserRepository userRepository, InstrumentsRepository instrumentsRepository, 
                        OrderRepository orderRepository, HoldingsRepository holdingsRepository,
                        AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.orderRepository = orderRepository;
        this.holdingsRepository = holdingsRepository;
        this.accountRepository = accountRepository;
    }


    @Transactional()
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
        if (request.getOrderType() == null || (!request.getOrderType().trim().equals("BUY") && !request.getOrderType().trim().equals("SELL"))) {
            throw new InvalidInputException("Order type must be BUY or SELL");
        }
        int clientId = user.getClientId();
        String ticker = request.getTicker().trim();
        BigDecimal quantity = request.getQuantity();
        String orderType = request.getOrderType().trim();
        int accountId = request.getAccountId();
        List<AccountResponse> balances = accountRepository.findAccountResponsesByAccountId(accountId);
        if(balances.size() == 0)
        {
            throw new InvalidCredentialsException("Account not found");
        }
        BigDecimal balance = balances.get(0).getBalance();
        // Execute parameterized query with user's clientId
        //
        OrderHistoryResult orderHistoryresult;
        List<PriceQuoteResult> prices = instrumentsRepository.searchInstrumentPrice(ticker);
        List<InstrumentIdResult> instrumentIds = instrumentsRepository.searchInstrumentId(ticker);
        if(prices.size() == 0){
            throw new TickerNotFoundException("Ticker not found");
        }
        BigDecimal cost = quantity.multiply(prices.get(0).getCurrentPrice());
        if(balance.compareTo(cost) < 0){
            throw new InvalidInputException("Cost too high for current balance");
        }
        List<TickerSearchResult> holdingInventory = holdingsRepository.searchByInstrumentQuery(clientId, ticker);
        Long tickerStock = 0l;
        for(TickerSearchResult tickerSearchResult : holdingInventory){
            if(tickerSearchResult.getAccountId() == accountId){
                tickerStock += tickerSearchResult.getQuantity();
            }
        }
        if(new BigDecimal(tickerStock).compareTo(quantity) < 0 && orderType.toUpperCase().equals("SELL")){
            throw new InvalidInputException("Not enough holding inventory in account to sell");
        }
        Orders order = new Orders(accountId, instrumentIds.get(0).getInstrumentId(), orderType, quantity, 
            prices.get(0).getCurrentPrice(), LocalDate.now());
        Orders savedOrder = orderRepository.save(order);
        savedOrder.setOrderStatus("ACCEPTED");
        orderRepository.save(savedOrder);
        List<OrderHistoryResult> result = new ArrayList<>();
        result.add(new OrderHistoryResult(savedOrder.getOrderId(), ticker, savedOrder.getOrderType(), savedOrder.getQuantity(), 
            savedOrder.getPrice(), savedOrder.getOrderStatus(), savedOrder.getOrderDate(), savedOrder.getSubmittedAt()));

        // Integer orderId, String ticker, String orderType,
        //                      BigDecimal quantity, BigDecimal price, String orderStatus,
        //                      LocalDate orderDate, LocalDateTime submittedAt) {
        return result;
    }
}
