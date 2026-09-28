package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.ForgotPasswordResponse;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final HoldingsRepository tickerRepository;
    public OrderService(HoldingsRepository tickerRepository, UserRepository userRepository,
                            InstrumentsRepository priceQuoteRepository, OrderRepository orderRepository) {
        this.tickerRepository = tickerRepository;
        this.userRepository = userRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.orderRepository = orderRepository;
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
        if (request.getQuantity() <= 0)
        {
            throw new InvalidInputException("Order quantity must be greater than 0");
        }
        if (request.getOrderType() == null || request.getOrderType().trim() != "BUY" || request.getOrderType().trim() != "SELL") {
            throw new InvalidInputException("Order type must be BUY or SELL");
        }
        
        String ticker = request.getTicker().trim();
        BigDecimal quantity = request.getQuantity();
        String orderType = request.getOrderType().trim();

        // Execute parameterized query with user's clientId
        //
        OrderHistoryResult orderHistoryresult;
        List<PriceQuoteResult> price = instrumentsRepository.findPricesToday(ticker);
        if(price.size() == 0){
            throw new NotFoundException("No price found for that ticker today");
        }
        // List<OrderHistoryResult> = orderRepository.
    }
}
