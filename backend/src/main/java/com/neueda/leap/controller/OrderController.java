package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.*;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/order")
public class OrderController {
        @Autowired
        private final OrderService orderService;
        private final JwtUtil jwtUtil;
        private static final String BEARER_PREFIX = "Bearer ";

        public OrderController(OrderService orderService, JwtUtil jwtUtil) {
            this.orderService = orderService;
            this.jwtUtil = jwtUtil;
        }
        @PostMapping("/placed")
        public ResponseEntity<?> makeOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody OrderRequest request) {
        try {
            // Extract userId from JWT token
            Integer userId = extractUserIdFromToken(authHeader);

            // Search with parameterized query
            List<OrderHistoryResult> placedOrder = orderService.placeOrder(userId, request);

            return ResponseEntity.ok(placedOrder);

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
