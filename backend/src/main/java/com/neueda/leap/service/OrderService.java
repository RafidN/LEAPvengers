package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidCredentialsException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.exception.TickerNotFoundException;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AuthenticationResponse;
import com.neueda.leap.model.dto.ForgotPasswordResponse;
import com.neueda.leap.model.dto.RegisterRequest;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.PriceQuoteResult;

import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;
    public OrderService(UserRepository userRepository, InstrumentsRepository instrumentsRepository, 
                        OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.instrumentsRepository = instrumentsRepository;
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
        if (request.getQuantity().compareTo(new BigDecimal(0)) <= 0)
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
        List<PriceQuoteResult> price = instrumentsRepository.searchInstrumentPrice(ticker);
        if(price.size() == 0){
            throw new TickerNotFoundException("No price found for that ticker today");
        }
        return new ArrayList<OrderHistoryResult>();
        // List<OrderHistoryResult> = orderRepository.
    }
}
