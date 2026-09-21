package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.*;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.HistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for historical data queries
 * Supports order history, cash transactions, price history, and portfolio history
 * Time periods: past year, past month, past 7 days, past day, today
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService historyService;
    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";

    public HistoryController(HistoryService historyService, JwtUtil jwtUtil) {
        this.historyService = historyService;
        this.jwtUtil = jwtUtil;
    }

    // ===== ORDER HISTORY ENDPOINTS (AUTHENTICATED) =====

    /**
     * Get user's order history from past year
     * 
     * POST /api/history/orders/past-year
     * Authorization: Bearer {JWT_TOKEN}
     * Response: [ { "orderId": 1, "ticker": "AAPL", ... } ]
     */
    @PostMapping("/orders/past-year")
    public ResponseEntity<?> getOrderHistoryPastYear(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<OrderHistoryResult> results = historyService.getOrderHistoryPastYear(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/orders/past-month")
    public ResponseEntity<?> getOrderHistoryPastMonth(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<OrderHistoryResult> results = historyService.getOrderHistoryPastMonth(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/orders/past-7-days")
    public ResponseEntity<?> getOrderHistoryPast7Days(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<OrderHistoryResult> results = historyService.getOrderHistoryPast7Days(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/orders/past-day")
    public ResponseEntity<?> getOrderHistoryPastDay(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<OrderHistoryResult> results = historyService.getOrderHistoryPastDay(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/orders/today")
    public ResponseEntity<?> getOrderHistoryToday(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<OrderHistoryResult> results = historyService.getOrderHistoryToday(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    // ===== CASH TRANSACTION HISTORY ENDPOINTS (AUTHENTICATED) =====

    @PostMapping("/cash/past-year")
    public ResponseEntity<?> getCashHistoryPastYear(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<CashTransactionResult> results = historyService.getCashHistoryPastYear(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/cash/past-month")
    public ResponseEntity<?> getCashHistoryPastMonth(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<CashTransactionResult> results = historyService.getCashHistoryPastMonth(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/cash/past-7-days")
    public ResponseEntity<?> getCashHistoryPast7Days(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<CashTransactionResult> results = historyService.getCashHistoryPast7Days(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/cash/past-day")
    public ResponseEntity<?> getCashHistoryPastDay(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<CashTransactionResult> results = historyService.getCashHistoryPastDay(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/cash/today")
    public ResponseEntity<?> getCashHistoryToday(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<CashTransactionResult> results = historyService.getCashHistoryToday(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    // ===== PRICE HISTORY ENDPOINTS (PUBLIC) =====

    /**
     * Get historical price data from past year
     * 
     * POST /api/history/prices/past-year
     * No authorization required
     * Request body: { "ticker": "AAPL" }
     * Response: [ { "ticker": "AAPL", "price": 150.00, ... } ]
     */
    @PostMapping("/prices/past-year")
    public ResponseEntity<?> getPriceHistoryPastYear(
            @RequestBody TickerSearchRequest request) {
        try {
            List<PriceHistoryResult> results = historyService.getPriceHistoryPastYear(request.getTicker());
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/prices/past-month")
    public ResponseEntity<?> getPriceHistoryPastMonth(
            @RequestBody TickerSearchRequest request) {
        try {
            List<PriceHistoryResult> results = historyService.getPriceHistoryPastMonth(request.getTicker());
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/prices/past-7-days")
    public ResponseEntity<?> getPriceHistoryPast7Days(
            @RequestBody TickerSearchRequest request) {
        try {
            List<PriceHistoryResult> results = historyService.getPriceHistoryPast7Days(request.getTicker());
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/prices/past-day")
    public ResponseEntity<?> getPriceHistoryPastDay(
            @RequestBody TickerSearchRequest request) {
        try {
            List<PriceHistoryResult> results = historyService.getPriceHistoryPastDay(request.getTicker());
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/prices/today")
    public ResponseEntity<?> getPriceHistoryToday(
            @RequestBody TickerSearchRequest request) {
        try {
            List<PriceHistoryResult> results = historyService.getPriceHistoryToday(request.getTicker());
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    // ===== PORTFOLIO HISTORY ENDPOINTS (AUTHENTICATED) =====

    @PostMapping("/portfolio/past-year")
    public ResponseEntity<?> getPortfolioHistoryPastYear(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<PortfolioHistoryResult> results = historyService.getPortfolioHistoryPastYear(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/portfolio/past-month")
    public ResponseEntity<?> getPortfolioHistoryPastMonth(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<PortfolioHistoryResult> results = historyService.getPortfolioHistoryPastMonth(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/portfolio/past-7-days")
    public ResponseEntity<?> getPortfolioHistoryPast7Days(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<PortfolioHistoryResult> results = historyService.getPortfolioHistoryPast7Days(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/portfolio/past-day")
    public ResponseEntity<?> getPortfolioHistoryPastDay(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<PortfolioHistoryResult> results = historyService.getPortfolioHistoryPastDay(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @PostMapping("/portfolio/today")
    public ResponseEntity<?> getPortfolioHistoryToday(
            @RequestHeader("Authorization") String authHeader) {
        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<PortfolioHistoryResult> results = historyService.getPortfolioHistoryToday(userId);
            return ResponseEntity.ok(results);
        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    /**
     * Extract userId from JWT token
     * Validates token signature and expiration
     */
    private Integer extractUserIdFromToken(String authHeader) throws TokenValidationException {
        
        if (authHeader == null || authHeader.isEmpty()) {
            throw new TokenValidationException("Missing Authorization header");
        }

        if (!authHeader.startsWith(BEARER_PREFIX)) {
            throw new TokenValidationException("Invalid Authorization format");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtUtil.validateToken(token)) {
            throw new TokenValidationException("Invalid or expired token");
        }

        try {
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            throw new TokenValidationException("Failed to extract userId from token");
        }
    }
}
