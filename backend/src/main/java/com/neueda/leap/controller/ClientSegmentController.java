package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.ClientSegmentQueryRequest;
import com.neueda.leap.model.dto.ClientSegmentResult;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.ClientSegmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoint for client segmentation queries.
 */
@RestController
@RequestMapping("/clients")
public class ClientSegmentController {

    private final ClientSegmentService clientSegmentService;
    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";

    public ClientSegmentController(ClientSegmentService clientSegmentService, JwtUtil jwtUtil) {
        this.clientSegmentService = clientSegmentService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Query client segments using current portfolio value and recent filled-order activity.
     *
     * POST /api/clients/segments
     * Authorization: Bearer {JWT_TOKEN}
     * Content-Type: application/json
     */
    @PostMapping("/segments")
    public ResponseEntity<?> getClientSegments(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) ClientSegmentQueryRequest request) {

        try {
            Integer userId = extractUserIdFromToken(authHeader);
            List<ClientSegmentResult> results = clientSegmentService.getClientSegments(userId, request);
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