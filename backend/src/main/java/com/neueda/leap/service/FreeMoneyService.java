package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.dto.FreeMoneyBattleStartResult;
import com.neueda.leap.model.dto.FreeMoneyHitRequest;
import com.neueda.leap.model.dto.FreeMoneyHitResult;
import com.neueda.leap.model.dto.FreeMoneyWinResult;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies Free Money battle effects against holdings. */
@Service
public class FreeMoneyService {
    private static final BigDecimal DEDUCTION_RATIO = new BigDecimal("0.10");
    private static final BigDecimal NORMAL_REWARD_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal HARD_REWARD_MULTIPLIER = new BigDecimal("2.0");
    private static final BigDecimal LUNATIC_REWARD_MULTIPLIER = new BigDecimal("3.0");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal MIN_QUANTITY_STEP = new BigDecimal("0.0001");

    private final UserRepository userRepository;
    private final OwnershipService ownershipService;
    private final InstrumentsRepository instrumentsRepository;
    private final HoldingsRepository holdingsRepository;

    private final Map<String, BattleState> battles = new ConcurrentHashMap<>();

    public FreeMoneyService(UserRepository userRepository,
                            OwnershipService ownershipService,
                            InstrumentsRepository instrumentsRepository,
                            HoldingsRepository holdingsRepository) {
        this.userRepository = userRepository;
        this.ownershipService = ownershipService;
        this.instrumentsRepository = instrumentsRepository;
        this.holdingsRepository = holdingsRepository;
    }

    @Transactional
    public FreeMoneyBattleStartResult startBattle(Integer userId, FreeMoneyHitRequest request)
            throws InvalidInputException, UserNotFoundException {
        validateUserAndRequest(userId, request, false);

        Integer accountId = request.getAccountId();
        String ticker = request.getTicker().trim().toUpperCase();

        ownershipService.requireAccount(accountId);

        Instruments instrument = instrumentsRepository.findByTickerIgnoreCase(ticker)
                .orElseThrow(() -> new InvalidInputException("Ticker not found"));

        Holdings holding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(accountId, instrument.getInstrumentId())
                .orElseThrow(() -> new InvalidInputException("No holding found for this account and ticker"));

        BigDecimal startingQuantity = holding.getQuantity().setScale(4, RoundingMode.HALF_UP);
        if (startingQuantity.compareTo(ZERO) <= 0) {
            throw new InvalidInputException("No holding available to start battle");
        }

        String battleId = UUID.randomUUID().toString();
        String difficulty = normalizeDifficulty(request.getDifficulty());
        BattleState battle = new BattleState();
        battle.battleId = battleId;
        battle.userId = userId;
        battle.accountId = accountId;
        battle.instrumentId = instrument.getInstrumentId();
        battle.ticker = ticker;
        battle.difficulty = difficulty;
        battle.rewardMultiplier = rewardMultiplierForDifficulty(difficulty);
        battle.startingQuantity = startingQuantity;
        battle.resolved = false;
        battle.startedAt = LocalDateTime.now();
        battles.put(battleId, battle);

        return new FreeMoneyBattleStartResult(
                battleId,
                accountId,
                ticker,
                startingQuantity,
                battle.startedAt,
                "Battle started"
        );
    }

    @Transactional
    public FreeMoneyHitResult applyHit(Integer userId, FreeMoneyHitRequest request)
            throws InvalidInputException, UserNotFoundException {
        BattleState battle = requireActiveBattle(userId, request);

        Holdings holding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(battle.accountId, battle.instrumentId)
                .orElseThrow(() -> new InvalidInputException("No holding found for this account and ticker"));

        BigDecimal before = holding.getQuantity().setScale(4, RoundingMode.HALF_UP);
        if (before.compareTo(ZERO) <= 0) {
            throw new InvalidInputException("No holding available for deduction");
        }

        BigDecimal deduction = battle.startingQuantity.multiply(DEDUCTION_RATIO).setScale(4, RoundingMode.HALF_UP);
        if (deduction.compareTo(ZERO) <= 0) {
            deduction = MIN_QUANTITY_STEP;
        }

        BigDecimal after = before.subtract(deduction).max(ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal deducted = before.subtract(after).setScale(4, RoundingMode.HALF_UP);

        if (after.compareTo(ZERO) == 0) {
            holdingsRepository.delete(holding);
        } else {
            holding.setQuantity(after);
            holding.setAsOfDate(LocalDate.now());
            holdingsRepository.save(holding);
        }

        return new FreeMoneyHitResult(
                battle.accountId,
                battle.ticker,
                before,
                deducted,
                after,
                LocalDateTime.now(),
                "Hit registered. Holding reduced by 10% of starting quantity."
        );
    }

    @Transactional
    public FreeMoneyWinResult applyBossDefeat(Integer userId, FreeMoneyHitRequest request)
            throws InvalidInputException, UserNotFoundException {
        BattleState battle = requireActiveBattle(userId, request);

        Holdings holding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(battle.accountId, battle.instrumentId)
                .orElseThrow(() -> new InvalidInputException("No holding found for this account and ticker"));

        BigDecimal before = holding.getQuantity().setScale(4, RoundingMode.HALF_UP);
        if (before.compareTo(ZERO) <= 0) {
            throw new InvalidInputException("No holding available for reward");
        }

        BigDecimal multiplier = battle.rewardMultiplier;
        BigDecimal after = before.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
        holding.setQuantity(after);
        holding.setAsOfDate(LocalDate.now());
        holdingsRepository.save(holding);

        battle.resolved = true;

        String multiplierLabel = multiplier.stripTrailingZeros().toPlainString();
        return new FreeMoneyWinResult(
                battle.accountId,
                battle.ticker,
                before,
                after,
            multiplier,
                LocalDateTime.now(),
            "Boss defeated. Holding multiplied by " + multiplierLabel + "x."
        );
    }

    @Transactional
    public FreeMoneyHitResult forfeitBattle(Integer userId, FreeMoneyHitRequest request)
            throws InvalidInputException, UserNotFoundException {
        BattleState battle = requireExistingBattle(userId, request);
        if (battle.resolved) {
            return new FreeMoneyHitResult(
                    battle.accountId,
                    battle.ticker,
                    ZERO,
                    ZERO,
                    ZERO,
                    LocalDateTime.now(),
                    "Battle already resolved"
            );
        }

        BigDecimal before = ZERO;
        BigDecimal after = ZERO;
        BigDecimal deducted = ZERO;

        Holdings holding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(battle.accountId, battle.instrumentId)
                .orElse(null);

        if (holding != null) {
            before = holding.getQuantity().setScale(4, RoundingMode.HALF_UP);
            after = before.subtract(battle.startingQuantity).max(ZERO).setScale(4, RoundingMode.HALF_UP);
            deducted = before.subtract(after).setScale(4, RoundingMode.HALF_UP);

            if (after.compareTo(ZERO) == 0) {
                holdingsRepository.delete(holding);
            } else {
                holding.setQuantity(after);
                holding.setAsOfDate(LocalDate.now());
                holdingsRepository.save(holding);
            }
        }

        battle.resolved = true;

        return new FreeMoneyHitResult(
                battle.accountId,
                battle.ticker,
                before,
                deducted,
                after,
                LocalDateTime.now(),
                "Battle forfeited. Starting holdings removed."
        );
    }

    private void validateUserAndRequest(Integer userId, FreeMoneyHitRequest request, boolean requireBattleId)
            throws InvalidInputException, UserNotFoundException {
        if (userId == null || userId <= 0) {
            throw new InvalidInputException("User is not authenticated");
        }
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));

        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }
        if (request.getAccountId() == null || request.getAccountId() <= 0) {
            throw new InvalidInputException("Account is required");
        }
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Ticker is required");
        }
        if (requireBattleId && (request.getBattleId() == null || request.getBattleId().isBlank())) {
            throw new InvalidInputException("Battle ID is required");
        }
    }

    private BattleState requireExistingBattle(Integer userId, FreeMoneyHitRequest request)
            throws InvalidInputException, UserNotFoundException {
        validateUserAndRequest(userId, request, true);

        String battleId = request.getBattleId().trim();
        BattleState battle = battles.get(battleId);
        if (battle == null) {
            throw new InvalidInputException("Battle not found");
        }
        if (!battle.userId.equals(userId)) {
            throw new InvalidInputException("Battle does not belong to current user");
        }
        if (!battle.accountId.equals(request.getAccountId())) {
            throw new InvalidInputException("Battle account mismatch");
        }
        if (!battle.ticker.equalsIgnoreCase(request.getTicker().trim())) {
            throw new InvalidInputException("Battle ticker mismatch");
        }

        ownershipService.requireAccount(battle.accountId);
        return battle;
    }

    private BattleState requireActiveBattle(Integer userId, FreeMoneyHitRequest request)
            throws InvalidInputException, UserNotFoundException {
        BattleState battle = requireExistingBattle(userId, request);
        if (battle.resolved) {
            throw new InvalidInputException("Battle is already resolved");
        }
        return battle;
    }

    private String normalizeDifficulty(String rawDifficulty) throws InvalidInputException {
        if (rawDifficulty == null || rawDifficulty.isBlank()) {
            return "normal";
        }

        String normalized = rawDifficulty.trim().toLowerCase();
        if (!normalized.equals("normal") && !normalized.equals("hard") && !normalized.equals("lunatic")) {
            throw new InvalidInputException("Difficulty must be normal, hard, or lunatic");
        }
        return normalized;
    }

    private BigDecimal rewardMultiplierForDifficulty(String difficulty) {
        return switch (difficulty) {
            case "hard" -> HARD_REWARD_MULTIPLIER;
            case "lunatic" -> LUNATIC_REWARD_MULTIPLIER;
            default -> NORMAL_REWARD_MULTIPLIER;
        };
    }

    private static final class BattleState {
        private String battleId;
        private Integer userId;
        private Integer accountId;
        private Integer instrumentId;
        private String ticker;
        private String difficulty;
        private BigDecimal rewardMultiplier;
        private BigDecimal startingQuantity;
        private LocalDateTime startedAt;
        private boolean resolved;
    }
}
