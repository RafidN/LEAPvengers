package com.neueda.leap.controller;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.model.dto.*;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.CurrentUserService;
import com.neueda.leap.service.OrderService;
import com.neueda.leap.service.ExchangeRateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


// Controller for placing orders, and for canceling or moving limit and stop orders that are still working.
// Reading orders back is OrderQueryController (GET /orders).
@RestController
@RequestMapping("/orders")
public class OrderController {
        private final OrderService orderService;
        private final ExchangeRateService exchangeRateService;
        private final JwtUtil jwtUtil;
        private final CurrentUserService currentUserService;
        private static final String BEARER_PREFIX = "Bearer ";

        public OrderController(OrderService orderService, ExchangeRateService exchangeRateService, JwtUtil jwtUtil,
                               CurrentUserService currentUserService) {
            this.orderService = orderService;
            this.exchangeRateService = exchangeRateService;
            this.jwtUtil = jwtUtil;
            this.currentUserService = currentUserService;
        }

        @GetMapping("/exchange-rate")
        public ResponseEntity<ExchangeRateService.ExchangeRate> getExchangeRate(
                @RequestParam String source,
                @RequestParam String target) {
            return ResponseEntity.ok(exchangeRateService.getRate(source, target));
        }

        @GetMapping("/exchange-rates")
        public ResponseEntity<Map<String, ExchangeRateService.ExchangeRate>> getExchangeRates(
                @RequestParam(defaultValue = "USD") String source) {
            return ResponseEntity.ok(exchangeRateService.getRates(source));
        }

    /** A new limit or stop price for a working order, and optionally a new quantity. */
    public record ReplaceOrderRequest(BigDecimal triggerPrice, BigDecimal quantity) {
    }

    /**
     * Cancels a WORKING limit or stop order and returns it as CANCELED. Errors go through GlobalExceptionHandler:
     * 403 for another trader's order, 422 (code NOT_WORKING) once it has been triggered, filled or canceled.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderHistoryResult> cancelOrder(@PathVariable("id") Integer orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(currentUserService.getCurrentUser().getUserId(), orderId));
    }

    /**
     * Moves a WORKING limit or stop order to a new price: the order is canceled and the new one returned, WORKING.
     * A new price that breaks a rule answers 422 with its code and leaves the old order working.
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderHistoryResult> replaceOrder(@PathVariable("id") Integer orderId,
                                                           @RequestBody ReplaceOrderRequest request) {
        return ResponseEntity.ok(orderService.replaceOrder(currentUserService.getCurrentUser().getUserId(), orderId,
                request.triggerPrice(), request.quantity()));
    }

        @PostMapping
        public ResponseEntity<?> makeOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody OrderRequest request) {
        try {
            // Extract userId from JWT token
            Integer userId = extractUserIdFromToken(authHeader);

            // The order comes back ACCEPTED; after a short delay it is filled, or fails if the price moved
            OrderHistoryResult order = orderService.placeOrder(userId, request);

            return ResponseEntity.ok(List.of(order));

        } catch (TokenValidationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Unauthorized: " + e.getMessage());
        } catch (TradingRuleException e) {
            // The order broke a trading rule: say which one, with a code clients can match on,
            // and the id of the REJECTED order when one was saved
            Map<String, Object> body = new HashMap<>();
            body.put("code", e.getCode());
            body.put("message", e.getMessage());
            if (e.getOrderId() != null) {
                body.put("orderId", e.getOrderId());
            }
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        } catch (ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: " + e.getMessage());
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
