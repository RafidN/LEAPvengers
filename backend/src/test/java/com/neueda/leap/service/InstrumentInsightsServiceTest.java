package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.InstrumentInsightsProjection;
import com.neueda.leap.model.dto.InstrumentInsightsQueryRequest;
import com.neueda.leap.model.dto.InstrumentInsightsResult;
import com.neueda.leap.repository.InstrumentInsightsRepository;
import com.neueda.leap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentInsightsServiceTest {

    @Mock
    private InstrumentInsightsRepository instrumentInsightsRepository;

    @Mock
    private UserRepository userRepository;

    private InstrumentInsightsService instrumentInsightsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        instrumentInsightsService = new InstrumentInsightsService(
                instrumentInsightsRepository,
                userRepository,
                new ClientSegmentRules()
        );
    }

    @Test
    void getInstrumentInsightsAppliesDefaultsAndMapsRows() throws Exception {
        Users user = new Users(null, "analyst", "secret");
        user.setUserId(7);
        user.setRole("ANALYST");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        InstrumentInsightsProjection projection = new StubInstrumentInsightsProjection(
                "AAPL",
                "Apple Inc.",
                "Equity",
                new BigDecimal("250000.00"),
                18L,
                10L,
                8L,
                6L
        );

        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(instrumentInsightsRepository.findInstrumentInsights(
                eq(90),
                eq(null),
                eq(ClientSegmentRules.DEFAULT_DORMANT_MAX_PORTFOLIO_VALUE),
                eq(ClientSegmentRules.DEFAULT_DORMANT_MAX_ORDER_COUNT),
                eq(ClientSegmentRules.DEFAULT_PREMIER_MIN_PORTFOLIO_VALUE),
                eq(ClientSegmentRules.DEFAULT_ACTIVE_MIN_ORDER_COUNT),
                eq(InstrumentInsightsService.DEFAULT_LIMIT)
        )).thenReturn(List.of(projection));

        List<InstrumentInsightsResult> results = instrumentInsightsService.getInstrumentInsights(7, new InstrumentInsightsQueryRequest());

        assertEquals(1, results.size());
        assertEquals("AAPL", results.get(0).getTicker());
        assertEquals(new BigDecimal("250000.00"), results.get(0).getCurrentPositionValue());
        assertEquals(18L, results.get(0).getRecentFilledOrderCount());
    }

    @Test
    void getInstrumentInsightsRejectsInvalidSegment() {
        Users user = new Users(null, "analyst", "secret");
        user.setUserId(7);
        when(userRepository.findById(7)).thenReturn(Optional.of(user));

        InstrumentInsightsQueryRequest request = new InstrumentInsightsQueryRequest();
        request.setSegment("Unknown");

        assertThrows(InvalidInputException.class, () -> instrumentInsightsService.getInstrumentInsights(7, request));
    }

    @Test
    void getInstrumentInsightsRejectsNonPositiveLimit() {
        Users user = new Users(null, "analyst", "secret");
        user.setUserId(7);
        when(userRepository.findById(7)).thenReturn(Optional.of(user));

        InstrumentInsightsQueryRequest request = new InstrumentInsightsQueryRequest();
        request.setLimit(0);

        assertThrows(InvalidInputException.class, () -> instrumentInsightsService.getInstrumentInsights(7, request));
    }

    @Test
    void getInstrumentInsightsThrowsWhenUserMissing() {
        when(userRepository.findById(7)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> instrumentInsightsService.getInstrumentInsights(7, new InstrumentInsightsQueryRequest()));
        verify(instrumentInsightsRepository, org.mockito.Mockito.never()).findInstrumentInsights(any(), any(), any(), any(), any(), any(), any());
    }

    private record StubInstrumentInsightsProjection(
            String ticker,
            String instrumentName,
            String assetClass,
            BigDecimal currentPositionValue,
            Long recentFilledOrderCount,
            Long buyCount,
            Long sellCount,
            Long tradedClientCount
    ) implements InstrumentInsightsProjection {
        @Override
        public String getTicker() {
            return ticker;
        }

        @Override
        public String getInstrumentName() {
            return instrumentName;
        }

        @Override
        public String getAssetClass() {
            return assetClass;
        }

        @Override
        public BigDecimal getCurrentPositionValue() {
            return currentPositionValue;
        }

        @Override
        public Long getRecentFilledOrderCount() {
            return recentFilledOrderCount;
        }

        @Override
        public Long getBuyCount() {
            return buyCount;
        }

        @Override
        public Long getSellCount() {
            return sellCount;
        }

        @Override
        public Long getTradedClientCount() {
            return tradedClientCount;
        }
    }
}