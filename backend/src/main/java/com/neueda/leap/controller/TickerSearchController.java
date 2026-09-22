package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchRequest;
import com.neueda.leap.model.dto.TickerSearchResult;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.TickerSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for ticker search
 * Requires JWT authentication via Authorization header
 */
@RestController
@RequestMapping("/api/search")
public class TickerSearchController {

    private final TickerSearchService tickerSearchService;
    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";

    public TickerSearchController(TickerSearchService tickerSearchService, JwtUtil jwtUtil) {
        this.tickerSearchService = tickerSearchService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Search held tickers (user's portfolio holdings)
     * 
     * POST /api/search/held-tickers
     * Authorization: Bearer {JWT_TOKEN}
     * Content-Type: application/json
     * 
     * Request body: { "ticker": "AAPL" }
     * Response: [ { "holdingId": 1, "ticker": "AAPL", "quantity": 100, ... } ]
     */
    @PostMapping("/held-tickers")
    public ResponseEntity<?> searchHeldTickers(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody TickerSearchRequest request) {

        try {
            // Extract userId from JWT token
            Integer userId = extractUserIdFromToken(authHeader);

            // Search with parameterized query
            List<TickerSearchResult> results = tickerSearchService.searchByTicker(userId, request);

            return ResponseEntity.ok(results);

        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (UserNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    /**
     * Search ticker price quote (public market data)
     * 
     * POST /api/search/ticker-price-quote
     * Content-Type: application/json
     * No authorization required
     * 
     * Request body: { "ticker": "AAPL" }
     * Response: [ { "ticker": "AAPL", "currentPrice": 185.50, ... } ]
     */
    @PostMapping("/ticker-price-quote")
    public ResponseEntity<?> searchTickerPriceQuote(
            @RequestBody TickerSearchRequest request) {

        try {
            // No JWT validation needed - this is public market data
            List<PriceQuoteResult> results = tickerSearchService.searchTickerPrice(request);
            return ResponseEntity.ok(results);

        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
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
            Integer userId = jwtUtil.extractUserId(token);
            return userId;
        } catch (Exception e) {
            throw new TokenValidationException("Invalid token");
        }
    }
}
