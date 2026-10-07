package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.CashTransactionResult;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.PortfolioHistoryResult;
import com.neueda.leap.model.dto.PriceHistoryResult;
import com.neueda.leap.security.SecurityContextHelper;
import com.neueda.leap.service.HistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for historical data queries.
 * Periods are selected with ?period=1d|7d|1m|1y. Omitting period returns all data up to now.
 */
@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryService historyService;
    private final SecurityContextHelper securityContextHelper;

    public HistoryController(HistoryService historyService,
            SecurityContextHelper securityContextHelper) {
        this.historyService = historyService;
        this.securityContextHelper = securityContextHelper;
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrderHistory(
            @RequestParam(value = "period", required = false) String period) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            List<OrderHistoryResult> results = historyService.getOrderHistory(userId, period);
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
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

    @GetMapping("/cash")
    public ResponseEntity<?> getCashHistory(
            @RequestParam(value = "period", required = false) String period) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            List<CashTransactionResult> results = historyService.getCashHistory(userId, period);
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
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

    @GetMapping("/prices")
    public ResponseEntity<?> getPriceHistory(
            @RequestParam(value = "ticker", required = false) String ticker,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "period", required = false) String period) {
        try {
            List<PriceHistoryResult> results = historyService.getPriceHistory(resolveQuery(ticker, query), period);
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Server error");
        }
    }

    @GetMapping("/portfolio")
    public ResponseEntity<?> getPortfolioHistory(
            @RequestParam(value = "period", required = false) String period) {
        try {
            Integer userId = securityContextHelper.getUserIdFromContext();
            List<PortfolioHistoryResult> results = historyService.getPortfolioHistory(userId, period);
            return ResponseEntity.ok(results);
        } catch (InvalidInputException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Invalid input: " + e.getMessage());
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

    private String resolveQuery(String ticker, String query) {
        if (ticker != null && !ticker.isBlank()) {
            return ticker;
        }
        return query;
    }
}
