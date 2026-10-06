package com.neueda.leap.controller;

import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.dto.QuoteResponse;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.InstrumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test suite for InstrumentController endpoints
 */
class InstrumentControllerTest {

    private MockMvc mockMvc;
    private StubJwtUtil stubJwtUtil;
    private StubInstrumentService stubInstrumentService;
    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String BEARER_PREFIX = "Bearer ";

    @BeforeEach
    void setUp() {
        stubJwtUtil = new StubJwtUtil();
        stubInstrumentService = new StubInstrumentService();
        InstrumentController controller = new InstrumentController(stubInstrumentService, stubJwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getInstruments_WithValidToken_ReturnsInstruments() throws Exception {
        List<Instruments> instruments = List.of(
            new Instruments(1, "AAPL", "Apple Inc.", "EQUITY", "NASDAQ"),
            new Instruments(2, "BTC", "Bitcoin", "CRYPTO", "SPOT")
        );
        stubInstrumentService.setInstruments(instruments);
        stubJwtUtil.setValidToken(VALID_TOKEN, true);

        mockMvc.perform(get("/instruments")
                .header("Authorization", BEARER_PREFIX + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$[1].ticker").value("BTC"));
    }

    @Test
    void getInstruments_WithoutToken_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/instruments"))
            .andExpect(status().isForbidden());
    }

    @Test
    void getInstruments_WithInvalidToken_ReturnsForbidden() throws Exception {
        stubJwtUtil.setValidToken(VALID_TOKEN, false);
        mockMvc.perform(get("/instruments")
                .header("Authorization", BEARER_PREFIX + VALID_TOKEN))
            .andExpect(status().isForbidden());
    }

    @Test
    void getQuote_WithValidId_ReturnsQuote() throws Exception {
        QuoteResponse quote = new QuoteResponse(
            new BigDecimal("150.25"),
            LocalDateTime.of(2026, 10, 1, 16, 30, 0)
        );
        stubInstrumentService.setQuoteForId(1, quote);
        stubJwtUtil.setValidToken(VALID_TOKEN, true);

        mockMvc.perform(get("/instruments/quotes/{instrumentId}", 1)
                .header("Authorization", BEARER_PREFIX + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(150.25));
    }

    @Test
    void getQuote_WithInvalidId_ReturnsBadRequest() throws Exception {
        stubJwtUtil.setValidToken(VALID_TOKEN, true);
        mockMvc.perform(get("/instruments/quotes/{instrumentId}", 0)
                .header("Authorization", BEARER_PREFIX + VALID_TOKEN))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getQuote_WithNonExistentId_ReturnsNotFound() throws Exception {
        stubInstrumentService.setQuoteNotFound(999);
        stubJwtUtil.setValidToken(VALID_TOKEN, true);

        mockMvc.perform(get("/instruments/quotes/{instrumentId}", 999)
                .header("Authorization", BEARER_PREFIX + VALID_TOKEN))
            .andExpect(status().isNotFound());
    }

    @Test
    void getQuote_WithoutToken_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/instruments/quotes/{instrumentId}", 1))
            .andExpect(status().isForbidden());
    }

    // ===== Stub Classes =====

    /**
     * Stub implementation of JwtUtil for testing
     */
    private static class StubJwtUtil extends JwtUtil {
        private String validToken;
        private boolean isValid;

        StubJwtUtil() {
        }

        void setValidToken(String token, boolean valid) {
            this.validToken = token;
            this.isValid = valid;
        }

        @Override
        public boolean validateToken(String token) {
            return token.equals(validToken) && isValid;
        }
    }

    /**
     * Stub implementation of InstrumentService for testing
     */
    private static class StubInstrumentService extends InstrumentService {
        private List<Instruments> instruments;
        private java.util.Map<Integer, QuoteResponse> quotes = new java.util.HashMap<>();
        private java.util.Set<Integer> notFoundIds = new java.util.HashSet<>();

        StubInstrumentService() {
            super(null, null);
        }

        void setInstruments(List<Instruments> instruments) {
            this.instruments = instruments;
        }

        void setQuoteForId(Integer instrumentId, QuoteResponse quote) {
            quotes.put(instrumentId, quote);
        }

        void setQuoteNotFound(Integer instrumentId) {
            notFoundIds.add(instrumentId);
        }

        @Override
        public List<Instruments> getInstruments() {
            return instruments;
        }

        @Override
        public QuoteResponse getQuoteByInstrumentId(Integer instrumentId) {
            if (notFoundIds.contains(instrumentId)) {
                throw new UserNotFoundException("No quote found for instrument ID: " + instrumentId);
            }
            return quotes.getOrDefault(instrumentId, new QuoteResponse(
                new BigDecimal("0.00"),
                LocalDateTime.now()
            ));
        }
    }
}
