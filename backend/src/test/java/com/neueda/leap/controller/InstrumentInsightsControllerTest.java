package com.neueda.leap.controller;

import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.InstrumentInsightsQueryRequest;
import com.neueda.leap.model.dto.InstrumentInsightsResult;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.InstrumentInsightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstrumentInsightsControllerTest {

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String BEARER_PREFIX = "Bearer ";

    private MockMvc mockMvc;
    private StubJwtUtil stubJwtUtil;
    private StubInstrumentInsightsService stubInstrumentInsightsService;

    @BeforeEach
    void setUp() {
        stubJwtUtil = new StubJwtUtil();
        stubInstrumentInsightsService = new StubInstrumentInsightsService();
        InstrumentInsightsController controller = new InstrumentInsightsController(stubInstrumentInsightsService, stubJwtUtil);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getInstrumentInsightsWithValidTokenReturnsRows() throws Exception {
        stubJwtUtil.setValidToken(VALID_TOKEN, true, 41);
        stubInstrumentInsightsService.setResults(List.of(
                new InstrumentInsightsResult(
                        "AAPL",
                        "Apple Inc.",
                        "Equity",
                        new BigDecimal("250000.00"),
                        18L,
                        10L,
                        8L,
                        6L
                )
        ));

        mockMvc.perform(post("/insights/instruments")
                        .header("Authorization", BEARER_PREFIX + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"lookbackDays\":90," +
                                "\"segment\":\"Active\"," +
                                "\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"))
                .andExpect(jsonPath("$[0].recentFilledOrderCount").value(18))
                .andExpect(jsonPath("$[0].tradedClientCount").value(6));
    }

    @Test
    void getInstrumentInsightsWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/insights/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInstrumentInsightsWithInvalidTokenReturnsUnauthorized() throws Exception {
        stubJwtUtil.setValidToken(VALID_TOKEN, false, 41);

        mockMvc.perform(post("/insights/instruments")
                        .header("Authorization", BEARER_PREFIX + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getInstrumentInsightsWithInvalidLookbackReturnsBadRequest() throws Exception {
        stubJwtUtil.setValidToken(VALID_TOKEN, true, 41);
        stubInstrumentInsightsService.setInvalidInput(new InvalidInputException("Lookback days must be positive"));

        mockMvc.perform(post("/insights/instruments")
                        .header("Authorization", BEARER_PREFIX + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lookbackDays\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getInstrumentInsightsWithEmptyResultsReturnsOk() throws Exception {
        stubJwtUtil.setValidToken(VALID_TOKEN, true, 41);
        stubInstrumentInsightsService.setResults(Collections.emptyList());

        mockMvc.perform(post("/insights/instruments")
                        .header("Authorization", BEARER_PREFIX + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    private static class StubJwtUtil extends JwtUtil {
        private String validToken;
        private boolean valid;
        private Integer userId;

        void setValidToken(String validToken, boolean valid, Integer userId) {
            this.validToken = validToken;
            this.valid = valid;
            this.userId = userId;
        }

        @Override
        public boolean validateToken(String token) {
            return valid && token.equals(validToken);
        }

        @Override
        public Integer extractUserId(String token) {
            if (!token.equals(validToken)) {
                throw new IllegalArgumentException("Invalid token");
            }
            return userId;
        }
    }

    private static class StubInstrumentInsightsService extends InstrumentInsightsService {
        private List<InstrumentInsightsResult> results = List.of();
        private InvalidInputException invalidInputException;
        private UserNotFoundException userNotFoundException;

        StubInstrumentInsightsService() {
            super(null, null, null);
        }

        void setResults(List<InstrumentInsightsResult> results) {
            this.results = results;
            this.invalidInputException = null;
            this.userNotFoundException = null;
        }

        void setInvalidInput(InvalidInputException invalidInputException) {
            this.invalidInputException = invalidInputException;
        }

        @Override
        public List<InstrumentInsightsResult> getInstrumentInsights(Integer userId, InstrumentInsightsQueryRequest request)
                throws UserNotFoundException, InvalidInputException {
            if (invalidInputException != null) {
                throw invalidInputException;
            }
            if (userNotFoundException != null) {
                throw userNotFoundException;
            }
            return results;
        }
    }
}