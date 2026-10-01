package com.neueda.leap.controller;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TokenValidationException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.Instruments;
import com.neueda.leap.model.dto.PriceQuoteResult;
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
    private String asset_class;


    public InstrumentController(InstrumentService instrumentService, JwtUtil jwtUtil) {
        this.instrumentService = instrumentService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<List<Instruments>> getInstruments(@RequestHeader("Authorization") String authorizationHeader) {
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

}