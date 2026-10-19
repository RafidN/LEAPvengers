package com.neueda.leap.service;

import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.FreeMoneyHitRequest;
import com.neueda.leap.model.dto.FreeMoneyHitResult;
import com.neueda.leap.model.dto.FreeMoneyBattleStartResult;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreeMoneyServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OwnershipService ownershipService;

    @Mock
    private InstrumentsRepository instrumentsRepository;

    @Mock
    private HoldingsRepository holdingsRepository;

    private FreeMoneyService freeMoneyService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        freeMoneyService = new FreeMoneyService(userRepository, ownershipService, instrumentsRepository, holdingsRepository);
    }

    @Test
    void applyHitReducesHoldingByTenPercent() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(1001);
        request.setTicker("AAPL");

        Users user = new Users();
        user.setUserId(55);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(2001);

        Holdings holding = new Holdings();
        holding.setAccountId(1001);
        holding.setInstrumentId(2001);
        holding.setQuantity(new BigDecimal("10.0000"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(55)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("AAPL")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(1001, 2001)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(55, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyHitResult result = freeMoneyService.applyHit(55, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("10.0000").compareTo(result.getQuantityBefore()));
        assertEquals(0, new BigDecimal("1.0000").compareTo(result.getQuantityDeducted()));
        assertEquals(0, new BigDecimal("9.0000").compareTo(result.getQuantityAfter()));

        verify(ownershipService, times(2)).requireAccount(1001);
        verify(holdingsRepository).save(any(Holdings.class));
        verify(holdingsRepository, never()).delete(any(Holdings.class));
    }

    @Test
    void applyHitUsesMinimumStepWhenTenPercentRoundsToZero() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(1002);
        request.setTicker("MSFT");

        Users user = new Users();
        user.setUserId(56);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(2002);

        Holdings holding = new Holdings();
        holding.setAccountId(1002);
        holding.setInstrumentId(2002);
        holding.setQuantity(new BigDecimal("0.0009"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(56)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("MSFT")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(1002, 2002)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(56, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyHitResult result = freeMoneyService.applyHit(56, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.0009").compareTo(result.getQuantityBefore()));
        assertEquals(0, new BigDecimal("0.0001").compareTo(result.getQuantityDeducted()));
        assertEquals(0, new BigDecimal("0.0008").compareTo(result.getQuantityAfter()));

        verify(holdingsRepository).save(any(Holdings.class));
        verify(holdingsRepository, never()).delete(any(Holdings.class));
    }

    @Test
    void applyHitDeletesHoldingWhenQuantityReachesZero() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(1003);
        request.setTicker("GOOG");

        Users user = new Users();
        user.setUserId(57);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(2003);

        Holdings holding = new Holdings();
        holding.setAccountId(1003);
        holding.setInstrumentId(2003);
        holding.setQuantity(new BigDecimal("0.0001"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(57)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("GOOG")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(1003, 2003)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(57, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyHitResult result = freeMoneyService.applyHit(57, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.0001").compareTo(result.getQuantityBefore()));
        assertEquals(0, new BigDecimal("0.0001").compareTo(result.getQuantityDeducted()));
        assertEquals(0, new BigDecimal("0.0000").compareTo(result.getQuantityAfter()));

        verify(holdingsRepository).delete(holding);
        verify(holdingsRepository, never()).save(any(Holdings.class));
    }

    @Test
    void forfeitRemovesStartingHoldingsAmount() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(1010);
        request.setTicker("TSLA");

        Users user = new Users();
        user.setUserId(58);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(2010);

        Holdings holding = new Holdings();
        holding.setAccountId(1010);
        holding.setInstrumentId(2010);
        holding.setQuantity(new BigDecimal("12.0000"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(58)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("TSLA")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(1010, 2010)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(58, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyHitResult result = freeMoneyService.forfeitBattle(58, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("12.0000").compareTo(result.getQuantityBefore()));
        assertEquals(0, new BigDecimal("12.0000").compareTo(result.getQuantityDeducted()));
        assertEquals(0, new BigDecimal("0.0000").compareTo(result.getQuantityAfter()));
        verify(holdingsRepository).delete(holding);
    }
}
