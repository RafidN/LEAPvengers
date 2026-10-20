package com.neueda.leap.service;

import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.FreeMoneyHitRequest;
import com.neueda.leap.model.dto.FreeMoneyBattleStartResult;
import com.neueda.leap.model.dto.FreeMoneyWinResult;
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

class FreeMoneyRewardServiceTest {

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
    void applyBossDefeatUsesNormalDifficultyMultiplierByDefault() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(4001);
        request.setTicker("NVDA");

        Users user = new Users();
        user.setUserId(88);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(5001);

        Holdings holding = new Holdings();
        holding.setAccountId(4001);
        holding.setInstrumentId(5001);
        holding.setQuantity(new BigDecimal("12.3456"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(88)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("NVDA")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(4001, 5001)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(88, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyWinResult result = freeMoneyService.applyBossDefeat(88, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("12.3456").compareTo(result.getQuantityBefore()));
        assertEquals(0, new BigDecimal("18.5184").compareTo(result.getQuantityAfter()));
        assertEquals(0, new BigDecimal("1.5").compareTo(result.getMultiplier()));

        verify(ownershipService, times(2)).requireAccount(4001);
        verify(holdingsRepository).save(any(Holdings.class));
        verify(holdingsRepository, never()).delete(any(Holdings.class));
    }

    @Test
    void applyBossDefeatUsesHardMultiplier() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(4002);
        request.setTicker("AMD");
        request.setDifficulty("hard");

        Users user = new Users();
        user.setUserId(89);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(5002);

        Holdings holding = new Holdings();
        holding.setAccountId(4002);
        holding.setInstrumentId(5002);
        holding.setQuantity(new BigDecimal("10.0000"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(89)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("AMD")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(4002, 5002)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(89, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyWinResult result = freeMoneyService.applyBossDefeat(89, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("20.0000").compareTo(result.getQuantityAfter()));
        assertEquals(0, new BigDecimal("2.0").compareTo(result.getMultiplier()));
    }

    @Test
    void applyBossDefeatUsesLunaticMultiplier() {
        FreeMoneyHitRequest request = new FreeMoneyHitRequest();
        request.setAccountId(4003);
        request.setTicker("TSLA");
        request.setDifficulty("lunatic");

        Users user = new Users();
        user.setUserId(90);

        Instruments instrument = new Instruments();
        instrument.setInstrumentId(5003);

        Holdings holding = new Holdings();
        holding.setAccountId(4003);
        holding.setInstrumentId(5003);
        holding.setQuantity(new BigDecimal("7.5000"));
        holding.setAsOfDate(LocalDate.now().minusDays(1));

        when(userRepository.findById(90)).thenReturn(Optional.of(user));
        when(instrumentsRepository.findByTickerIgnoreCase("TSLA")).thenReturn(Optional.of(instrument));
        when(holdingsRepository.findByAccountIdAndInstrumentIdForUpdate(4003, 5003)).thenReturn(Optional.of(holding));

        FreeMoneyBattleStartResult battle = freeMoneyService.startBattle(90, request);
        request.setBattleId(battle.getBattleId());

        FreeMoneyWinResult result = freeMoneyService.applyBossDefeat(90, request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("22.5000").compareTo(result.getQuantityAfter()));
        assertEquals(0, new BigDecimal("3.0").compareTo(result.getMultiplier()));
    }
}
