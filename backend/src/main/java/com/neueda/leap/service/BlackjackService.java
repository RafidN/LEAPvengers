package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.dto.BlackjackHandState;
import com.neueda.leap.model.dto.BlackjackPlayRequest;
import com.neueda.leap.model.dto.BlackjackRoundResult;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stateful blackjack rounds tied to real holdings quantity.
 */
@Service
public class BlackjackService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
    private static final BigDecimal NATURAL_BLACKJACK_PAYOUT = new BigDecimal("2.5");

    private final UserRepository userRepository;
    private final OwnershipService ownershipService;
    private final InstrumentsRepository instrumentsRepository;
    private final LatestPriceQuotesRepository latestPriceQuotesRepository;
    private final HoldingsRepository holdingsRepository;

    private final Map<String, RoundState> rounds = new ConcurrentHashMap<>();

    public BlackjackService(UserRepository userRepository,
                            OwnershipService ownershipService,
                            InstrumentsRepository instrumentsRepository,
                            LatestPriceQuotesRepository latestPriceQuotesRepository,
                            HoldingsRepository holdingsRepository) {
        this.userRepository = userRepository;
        this.ownershipService = ownershipService;
        this.instrumentsRepository = instrumentsRepository;
        this.latestPriceQuotesRepository = latestPriceQuotesRepository;
        this.holdingsRepository = holdingsRepository;
    }

    @Transactional
    public BlackjackRoundResult startRound(Integer userId, BlackjackPlayRequest request)
            throws UserNotFoundException, InvalidInputException {
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
        if (request.getBetQuantity() == null || request.getBetQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidInputException("Bet quantity must be greater than 0");
        }

        Integer accountId = request.getAccountId();
        String ticker = request.getTicker().trim().toUpperCase();
        BigDecimal betQuantity = request.getBetQuantity().setScale(4, RoundingMode.HALF_UP);

        ownershipService.requireAccount(accountId);

        Instruments instrument = instrumentsRepository.findByTickerIgnoreCase(ticker)
                .orElseThrow(() -> new InvalidInputException("Ticker not found"));
        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(instrument.getInstrumentId())
                .orElseThrow(() -> new InvalidInputException("No market price found for ticker"));

        Holdings lockedHolding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(accountId, instrument.getInstrumentId())
                .orElseThrow(() -> new InvalidInputException("No holding found for this account and ticker"));

        BigDecimal quantityBefore = lockedHolding.getQuantity().setScale(4, RoundingMode.HALF_UP);
        if (quantityBefore.compareTo(betQuantity) < 0) {
            throw new InvalidInputException("Bet exceeds available holding quantity");
        }

        BigDecimal quantityCurrent = applyHoldingDelta(accountId, instrument.getInstrumentId(), betQuantity.negate());

        RoundState round = new RoundState();
        round.roundId = UUID.randomUUID().toString();
        round.userId = userId;
        round.accountId = accountId;
        round.instrumentId = instrument.getInstrumentId();
        round.ticker = ticker;
        round.betQuantity = betQuantity;
        round.priceSnapshot = quote.getPrice().setScale(4, RoundingMode.HALF_UP);
        round.quantityBefore = quantityBefore;
        round.quantityCurrent = quantityCurrent;
        round.reservedQuantity = betQuantity;
        round.startedAt = LocalDateTime.now();

        PlayerHand firstHand = new PlayerHand();
        firstHand.draw();
        firstHand.draw();
        round.playerHands.add(firstHand);
        round.dealerCards.add(drawCard());
        round.dealerCards.add(drawCard());

        if (isNaturalBlackjack(firstHand)) {
            firstHand.stood = true;
            settleRound(round);
            rounds.put(round.roundId, round);
            return toResponse(round, round.message);
        }

        rounds.put(round.roundId, round);

        return toResponse(round, "Round started. Choose hit, stand, or split if available.");
    }

    @Transactional
    public BlackjackRoundResult hit(Integer userId, String roundId) {
        RoundState round = getActiveRound(userId, roundId);
        ensureActionable(round);

        PlayerHand hand = currentHand(round);
        hand.draw();

        int total = hand.total();
        if (total > 21) {
            hand.outcome = "LOSE";
            hand.stood = true;
            moveToNextHand(round);
        } else if (total == 21) {
            hand.stood = true;
            moveToNextHand(round);
        }

        if (allHandsClosed(round)) {
            settleRound(round);
            return toResponse(round, round.message);
        }

        return toResponse(round, "Card drawn.");
    }

    @Transactional
    public BlackjackRoundResult stand(Integer userId, String roundId) {
        RoundState round = getActiveRound(userId, roundId);
        ensureActionable(round);

        PlayerHand hand = currentHand(round);
        hand.stood = true;
        moveToNextHand(round);

        if (allHandsClosed(round)) {
            settleRound(round);
            return toResponse(round, round.message);
        }

        return toResponse(round, "Hand stood. Move to next hand.");
    }

    @Transactional
    public BlackjackRoundResult split(Integer userId, String roundId) {
        RoundState round = getActiveRound(userId, roundId);
        ensureActionable(round);

        PlayerHand hand = currentHand(round);
        if (!canSplit(round)) {
            throw new InvalidInputException("Split is not available for this hand");
        }

        Optional<Holdings> lockedHolding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(round.accountId, round.instrumentId);
        BigDecimal available = lockedHolding.map(h -> h.getQuantity().setScale(4, RoundingMode.HALF_UP)).orElse(ZERO);
        if (available.compareTo(round.betQuantity) < 0) {
            throw new InvalidInputException("Not enough remaining holding quantity to split");
        }

        round.quantityCurrent = applyHoldingDelta(round.accountId, round.instrumentId, round.betQuantity.negate());
        round.reservedQuantity = round.reservedQuantity.add(round.betQuantity).setScale(4, RoundingMode.HALF_UP);

        String movedCard = hand.cards.remove(1);

        PlayerHand splitHand = new PlayerHand();
        splitHand.cards.add(movedCard);

        hand.draw();
        splitHand.draw();

        hand.fromSplit = true;
        splitHand.fromSplit = true;

        round.playerHands.add(round.currentHandIndex + 1, splitHand);

        return toResponse(round, "Hand split. Play current hand first.");
    }

    private void settleRound(RoundState round) {
        while (dealerTotal(round) < 17) {
            round.dealerCards.add(drawCard());
        }

        int dealerTotal = dealerTotal(round);
        int wins = 0;
        int blackjackWins = 0;
        int pushes = 0;

        for (PlayerHand hand : round.playerHands) {
            int playerTotal = hand.total();
            boolean naturalBlackjack = isNaturalBlackjack(hand);
            if (playerTotal > 21) {
                hand.outcome = "LOSE";
                continue;
            }
            if (dealerTotal > 21 || playerTotal > dealerTotal) {
                hand.outcome = "WIN";
                if (naturalBlackjack) {
                    blackjackWins++;
                } else {
                    wins++;
                }
                continue;
            }
            if (playerTotal == dealerTotal) {
                hand.outcome = "PUSH";
                pushes++;
                continue;
            }
            hand.outcome = "LOSE";
        }

        BigDecimal basePayoutMultiplier = BigDecimal.valueOf((wins * 2L) + pushes)
            .add(BigDecimal.valueOf(blackjackWins).multiply(NATURAL_BLACKJACK_PAYOUT));

        BigDecimal payoutQuantity = round.betQuantity
            .multiply(basePayoutMultiplier)
                .setScale(4, RoundingMode.HALF_UP);

        round.quantityCurrent = applyHoldingDelta(round.accountId, round.instrumentId, payoutQuantity);
        round.quantityAfter = round.quantityCurrent;
        round.resolved = true;
        round.currentHandIndex = -1;

        int totalWins = wins + blackjackWins;

        if (blackjackWins > 0 && totalWins == 1 && pushes == 0 && round.playerHands.size() == 1) {
            round.outcomeSummary = "WIN";
            round.message = "Blackjack! Dealer played out. Payout is 2.5x your bet.";
        } else if (totalWins > 0 && pushes == 0) {
            round.outcomeSummary = "WIN";
            round.message = "You won at least one hand and doubled those stakes.";
        } else if (totalWins == 0 && pushes > 0) {
            round.outcomeSummary = "PUSH";
            round.message = "Push or loss only. Push hands returned stake.";
        } else if (totalWins > 0) {
            round.outcomeSummary = "MIXED";
            round.message = "Mixed result across split hands.";
        } else {
            round.outcomeSummary = "LOSE";
            round.message = "You lost this round.";
        }
    }

    private BlackjackRoundResult toResponse(RoundState round, String message) {
        List<BlackjackHandState> handStates = new ArrayList<>();
        for (PlayerHand hand : round.playerHands) {
            handStates.add(new BlackjackHandState(new ArrayList<>(hand.cards), hand.total(), hand.outcome));
        }

        BigDecimal wagerValue = round.reservedQuantity.multiply(round.priceSnapshot).setScale(4, RoundingMode.HALF_UP);

        return new BlackjackRoundResult(
                round.roundId,
                round.resolved ? "RESOLVED" : "IN_PROGRESS",
                round.accountId,
                round.ticker,
                round.betQuantity,
                round.priceSnapshot,
                wagerValue,
                round.outcomeSummary,
                handStates,
                new ArrayList<>(round.dealerCards),
                dealerTotal(round),
                round.resolved ? null : round.currentHandIndex,
                !round.resolved,
                !round.resolved,
                canSplit(round),
                round.quantityBefore,
                round.quantityCurrent,
                round.resolved ? round.quantityAfter : null,
                LocalDateTime.now(),
                message
        );
    }

    private RoundState getActiveRound(Integer userId, String roundId) {
        if (roundId == null || roundId.isBlank()) {
            throw new InvalidInputException("Round ID is required");
        }
        RoundState round = rounds.get(roundId);
        if (round == null) {
            throw new InvalidInputException("Round not found");
        }
        if (!round.userId.equals(userId)) {
            throw new InvalidInputException("Round does not belong to current user");
        }
        ownershipService.requireAccount(round.accountId);
        return round;
    }

    private void ensureActionable(RoundState round) {
        if (round.resolved) {
            throw new InvalidInputException("Round is already resolved");
        }
        if (round.currentHandIndex < 0 || round.currentHandIndex >= round.playerHands.size()) {
            throw new InvalidInputException("No active hand to play");
        }
    }

    private PlayerHand currentHand(RoundState round) {
        return round.playerHands.get(round.currentHandIndex);
    }

    private boolean allHandsClosed(RoundState round) {
        for (PlayerHand hand : round.playerHands) {
            if (!hand.stood && hand.total() <= 21) {
                return false;
            }
        }
        return true;
    }

    private void moveToNextHand(RoundState round) {
        for (int i = round.currentHandIndex + 1; i < round.playerHands.size(); i++) {
            PlayerHand hand = round.playerHands.get(i);
            if (!hand.stood && hand.total() <= 21) {
                round.currentHandIndex = i;
                return;
            }
        }
        round.currentHandIndex = round.playerHands.size();
    }

    private boolean canSplit(RoundState round) {
        if (round.resolved) {
            return false;
        }
        if (round.currentHandIndex < 0 || round.currentHandIndex >= round.playerHands.size()) {
            return false;
        }
        PlayerHand hand = round.playerHands.get(round.currentHandIndex);
        if (hand.cards.size() != 2 || hand.stood) {
            return false;
        }
        String rank1 = rank(hand.cards.get(0));
        String rank2 = rank(hand.cards.get(1));
        return rank1.equals(rank2);
    }

    private boolean isNaturalBlackjack(PlayerHand hand) {
        return !hand.fromSplit && hand.cards.size() == 2 && hand.total() == 21;
    }

    private BigDecimal applyHoldingDelta(Integer accountId, Integer instrumentId, BigDecimal delta) {
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            return holdingsRepository
                    .findByAccountIdAndInstrumentIdForUpdate(accountId, instrumentId)
                    .map(h -> h.getQuantity().setScale(4, RoundingMode.HALF_UP))
                    .orElse(ZERO);
        }

        Holdings holding = holdingsRepository
                .findByAccountIdAndInstrumentIdForUpdate(accountId, instrumentId)
                .orElse(null);

        if (holding == null) {
            if (delta.compareTo(BigDecimal.ZERO) <= 0) {
                throw new InvalidInputException("No holding available for deduction");
            }
            Holdings created = new Holdings(accountId, instrumentId, delta.setScale(4, RoundingMode.HALF_UP), LocalDate.now());
            holdingsRepository.save(created);
            return created.getQuantity().setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal current = holding.getQuantity().setScale(4, RoundingMode.HALF_UP);
        BigDecimal next = current.add(delta).setScale(4, RoundingMode.HALF_UP);
        if (next.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Holding quantity cannot go below zero");
        }
        if (next.compareTo(BigDecimal.ZERO) == 0) {
            holdingsRepository.delete(holding);
            return ZERO;
        }
        holding.setQuantity(next);
        holding.setAsOfDate(LocalDate.now());
        holdingsRepository.save(holding);
        return next;
    }

    private int dealerTotal(RoundState round) {
        return total(round.dealerCards);
    }

    private int total(List<String> cards) {
        int sum = 0;
        int aces = 0;
        for (String card : cards) {
            String cardRank = rank(card);
            switch (cardRank) {
                case "A" -> {
                    sum += 11;
                    aces++;
                }
                case "K", "Q", "J" -> sum += 10;
                default -> sum += Integer.parseInt(cardRank);
            }
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    private String drawCard() {
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        String[] suits = {"H", "D", "C", "S"};
        String cardRank = ranks[ThreadLocalRandom.current().nextInt(ranks.length)];
        String suit = suits[ThreadLocalRandom.current().nextInt(suits.length)];
        return cardRank + suit;
    }

    private String rank(String card) {
        return card.substring(0, card.length() - 1);
    }

    private static final class PlayerHand {
        private final List<String> cards = new ArrayList<>();
        private boolean stood;
        private boolean fromSplit;
        private String outcome = "PENDING";

        void draw() {
            String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
            String[] suits = {"H", "D", "C", "S"};
            String cardRank = ranks[ThreadLocalRandom.current().nextInt(ranks.length)];
            String suit = suits[ThreadLocalRandom.current().nextInt(suits.length)];
            cards.add(cardRank + suit);
        }

        int total() {
            int sum = 0;
            int aces = 0;
            for (String card : cards) {
                String cardRank = card.substring(0, card.length() - 1);
                switch (cardRank) {
                    case "A" -> {
                        sum += 11;
                        aces++;
                    }
                    case "K", "Q", "J" -> sum += 10;
                    default -> sum += Integer.parseInt(cardRank);
                }
            }
            while (sum > 21 && aces > 0) {
                sum -= 10;
                aces--;
            }
            return sum;
        }
    }

    private static final class RoundState {
        private String roundId;
        private Integer userId;
        private Integer accountId;
        private Integer instrumentId;
        private String ticker;
        private BigDecimal betQuantity;
        private BigDecimal priceSnapshot;
        private BigDecimal quantityBefore;
        private BigDecimal quantityCurrent;
        private BigDecimal quantityAfter;
        private BigDecimal reservedQuantity;
        private String outcomeSummary = "PENDING";
        private String message = "Round started";
        private LocalDateTime startedAt;
        private final List<PlayerHand> playerHands = new ArrayList<>();
        private final List<String> dealerCards = new ArrayList<>();
        private int currentHandIndex = 0;
        private boolean resolved;
    }
}
