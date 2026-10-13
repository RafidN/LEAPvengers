package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.InstrumentInsightsQueryRequest;
import com.neueda.leap.model.dto.InstrumentInsightsResult;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.InstrumentInsightsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoint for analyst-facing instrument insights queries.
 */
@RestController
@RequestMapping("/insights")
public class InstrumentInsightsController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final InstrumentInsightsService instrumentInsightsService;
    private final JwtUtil jwtUtil;

    public InstrumentInsightsController(InstrumentInsightsService instrumentInsightsService, JwtUtil jwtUtil) {
        this.instrumentInsightsService = instrumentInsightsService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/instruments")
    public ResponseEntity<?> getInstrumentInsights(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) InstrumentInsightsQueryRequest request) {

        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<InstrumentInsightsResult> results = instrumentInsightsService.getInstrumentInsights(userId, request);
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