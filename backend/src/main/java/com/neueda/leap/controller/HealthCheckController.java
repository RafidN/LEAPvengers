package com.neueda.leap.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check controller for the LEAPvengers backend.
 * Provides basic health status endpoint.
 */
@RestController
@RequestMapping("/health")
public class HealthCheckController {

    /**
     * Health check endpoint.
     *
     * @return Health status response
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "LEAPvengers Backend");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

}
