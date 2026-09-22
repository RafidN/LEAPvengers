package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.InstrumentSearchRequest;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.TickerSearchResult;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.InstrumentSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for instrument search.
 * Requires JWT authentication via Authorization header for holdings search.
 */
@RestController
@RequestMapping("/api/search")
public class InstrumentSearchController {

    private final InstrumentSearchService instrumentSearchService;
    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";

    public InstrumentSearchController(InstrumentSearchService instrumentSearchService, JwtUtil jwtUtil) {
        this.instrumentSearchService = instrumentSearchService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Search held instruments in the user's portfolio.
     *
     * POST /api/search/held-instruments
     * Authorization: Bearer {JWT_TOKEN}
     * Content-Type: application/json
     *
     * Request body: { "query": "AAPL" }
     * Response: [ { "holdingId": 1, "ticker": "AAPL", "quantity": 100, ... } ]
     */
    @PostMapping({"/held-instruments", "/held-tickers"})
    public ResponseEntity<?> searchHeldInstruments(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody InstrumentSearchRequest request) {

        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<TickerSearchResult> results = instrumentSearchService.searchHoldings(userId, request);

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
     * Search instrument price quotes using a ticker symbol or full instrument name.
     *
     * POST /api/search/instrument-price-quote
     * Content-Type: application/json
     * No authorization required
     *
     * Request body: { "query": "AAPL" }
     * Response: [ { "ticker": "AAPL", "currentPrice": 185.50, ... } ]
     */
    @PostMapping({"/instrument-price-quote", "/ticker-price-quote"})
    public ResponseEntity<?> searchInstrumentPriceQuote(
            @RequestBody InstrumentSearchRequest request) {

        try {
            List<PriceQuoteResult> results = instrumentSearchService.searchInstrumentPrice(request);
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
     * Extract userId from JWT token.
     * Validates token signature and expiration.
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
            throw new TokenValidationException("Invalid token");
        }
    }
}