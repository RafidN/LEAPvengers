package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.dto.QuoteResponse;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.InstrumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final JwtUtil jwtUtil;
    private static final String BEARER_PREFIX = "Bearer ";


    public InstrumentController(InstrumentService instrumentService, JwtUtil jwtUtil) {
        this.instrumentService = instrumentService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Get all tradable instruments with asset class and market
     * 
     * @param authorizationHeader JWT token in Authorization header
     * @return List of all instruments
     */
    @GetMapping
    public ResponseEntity<List<Instruments>> getInstruments(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new TokenValidationException("Invalid Authorization header");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!jwtUtil.validateToken(token)) {
            throw new TokenValidationException("Invalid token");
        }
        List<Instruments> instruments = instrumentService.getInstruments();
        return new ResponseEntity<>(instruments, HttpStatus.OK);
    }

    /**
     * Get the latest price and timestamp for an instrument
     * 
     * @param instrumentId The instrument ID
     * @param authorizationHeader JWT token in Authorization header
     * @return Quote with price and timestamp
     * @throws UserNotFoundException if no quote found (404)
     */
    @GetMapping("/quotes/{instrumentId}")
    public ResponseEntity<QuoteResponse> getQuote(
            @PathVariable Integer instrumentId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new TokenValidationException("Invalid Authorization header");
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!jwtUtil.validateToken(token)) {
            throw new TokenValidationException("Invalid token");
        }
        if (instrumentId == null || instrumentId <= 0) {
            throw new InvalidInputException("Invalid instrument ID");
        }
        QuoteResponse quote = instrumentService.getQuoteByInstrumentId(instrumentId);
        return new ResponseEntity<>(quote, HttpStatus.OK);
    }

}