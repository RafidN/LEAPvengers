package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.dto.AccountHoldingResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.BlackjackState;
import com.neueda.leap.security.SecurityContextHelper;
import com.neueda.leap.service.blackjack.BasicStrategy;
import com.neueda.leap.service.blackjack.BlackjackTable;
import com.neueda.leap.service.blackjack.Hand;
import com.neueda.leap.service.blackjack.Shoe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Risk Lab: blackjack with play money, as a lesson in expected value. A trader sits down with 1,000 chips, or
 * "pretend-stakes" one of their real holdings (chips = its value, nothing is moved), plays real blackjack with a
 * basic-strategy coach, and is shown their chips against what their real portfolio did over the same minutes.
 * <p>
 * It never touches an account, a holding, cash or a trade record. Seats live in memory: a restart clears them,
 * which costs nothing because nothing real was at stake. Seats idle for 12 hours are cleared too.
 */
@Service
public class BlackjackService {
    static final BigDecimal DEFAULT_CHIPS = new BigDecimal("1000");
    private static final Duration IDLE_LIMIT = Duration.ofHours(12);

    private final AccountService accountService;
    private final SecurityContextHelper securityContextHelper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, Seat> seats = new ConcurrentHashMap<>();

    private static final class Seat {
        BlackjackTable table;
        LocalDateTime seatedAt;
        LocalDateTime lastActive;
        BigDecimal portfolioAtStart;
        String stakeTicker;
        BigDecimal stakeQuantity;
        BigDecimal stakePrice;
    }

    @Autowired
    public BlackjackService(AccountService accountService, SecurityContextHelper securityContextHelper) {
        this(accountService, securityContextHelper, Clock.systemDefaultZone());
    }

    BlackjackService(AccountService accountService, SecurityContextHelper securityContextHelper, Clock clock) {
        this.accountService = accountService;
        this.securityContextHelper = securityContextHelper;
        this.clock = clock;
    }

    public BlackjackState state() {
        Seat seat = seats.get(userId());
        return seat == null ? BlackjackState.notSeated() : view(seat);
    }

    /**
     * Sits down with fresh chips: 1,000, or the value of one of the trader's holdings when stakeTicker names one
     * ("what if I'd brought my 30 NFLX to the casino?"). Leaving and sitting down again starts over.
     */
    public BlackjackState sitDown(String stakeTicker) {
        Integer userId = userId();
        Seat seat = new Seat();
        seat.seatedAt = seat.lastActive = LocalDateTime.now(clock);
        seat.portfolioAtStart = portfolioValue();

        BigDecimal chips = DEFAULT_CHIPS;
        if (stakeTicker != null && !stakeTicker.isBlank()) {
            AccountHoldingResponse holding = holdings().stream()
                    .filter(h -> h.getTicker().equalsIgnoreCase(stakeTicker.trim()))
                    .filter(h -> h.getLatestPrice() != null && h.getQuantity().signum() > 0)
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputException("You don't hold any " + stakeTicker.trim()));
            chips = holding.getQuantity().multiply(holding.getLatestPrice()).setScale(0, RoundingMode.DOWN);
            if (chips.compareTo(BlackjackTable.MIN_BET) < 0) {
                throw new InvalidInputException("Your " + holding.getTicker() + " is worth less than one bet");
            }
            seat.stakeTicker = holding.getTicker();
            seat.stakeQuantity = holding.getQuantity();
            seat.stakePrice = holding.getLatestPrice();
        }
        seat.table = new BlackjackTable(chips, () -> Shoe.shuffled(random));
        seats.put(userId, seat);
        evictIdleSeats();
        return view(seat);
    }

    public BlackjackState deal(BigDecimal bet) {
        return play(table -> table.deal(bet));
    }

    public BlackjackState hit() {
        return play(BlackjackTable::hit);
    }

    public BlackjackState stand() {
        return play(BlackjackTable::stand);
    }

    public BlackjackState doubleDown() {
        return play(BlackjackTable::doubleDown);
    }

    public BlackjackState split() {
        return play(BlackjackTable::split);
    }

    public void leave() {
        seats.remove(userId());
    }

    private BlackjackState play(Consumer<BlackjackTable> move) {
        Seat seat = seats.get(userId());
        if (seat == null) {
            throw new InvalidInputException("Sit down at the table first");
        }
        // One move at a time per seat, even if the page double-clicks
        synchronized (seat) {
            move.accept(seat.table);
            seat.lastActive = LocalDateTime.now(clock);
            return view(seat);
        }
    }

    private BlackjackState view(Seat seat) {
        BlackjackTable t = seat.table;
        List<Hand> hands = t.hands();
        List<BlackjackState.HandView> handViews = hands.stream()
                .map(h -> new BlackjackState.HandView(h.cards().stream().map(c -> c.code()).toList(), h.total(), h.soft(),
                        h.bet(), h.doubled(), h.outcome().name(), h.payout()))
                .toList();
        List<String> dealerCards = t.dealerCards().stream().map(c -> c == null ? null : c.code()).toList();
        BlackjackState.Table table = new BlackjackState.Table(t.chips(), t.onTable(), t.inRound(), handViews,
                t.inRound() ? t.currentHand() : null, dealerCards, t.dealerShowing(), t.canDeal(), t.canHitOrStand(),
                t.canHitOrStand(), t.canDouble(), t.canSplit(), t.message(), t.cardsLeftInShoe(),
                BlackjackTable.MIN_BET, BlackjackTable.MAX_BET);

        BasicStrategy.Advice advice = t.advice();
        BlackjackTable.Feedback last = t.lastFeedback();
        BlackjackState.Coach coach = new BlackjackState.Coach(
                advice == null ? null : advice.action().name(), advice == null ? null : advice.situation(),
                last == null ? null : last.taken().name(), last == null ? null : last.recommended().name(),
                last == null ? null : last.situation(), last != null && last.followed());

        BigDecimal stack = t.chips().add(t.onTable());
        BigDecimal net = stack.subtract(t.startingChips());
        BlackjackState.Stats stats = new BlackjackState.Stats(t.roundsPlayed(), t.wagered(), net,
                percent(net, t.startingChips()), t.decisions(), t.decisionsByTheBook(),
                BlackjackTable.HOUSE_EDGE.movePointRight(2),
                t.wagered().multiply(BlackjackTable.HOUSE_EDGE).setScale(2, RoundingMode.HALF_UP));

        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal portfolioNow = portfolioValue();
        BigDecimal change = portfolioNow.subtract(seat.portfolioAtStart);
        BigDecimal stakeNow = seat.stakePrice == null ? null : stack.divide(seat.stakePrice, 4, RoundingMode.DOWN);
        BlackjackState.RealityCheck reality = new BlackjackState.RealityCheck(seat.seatedAt,
                Duration.between(seat.seatedAt, now).toMinutes(), seat.portfolioAtStart, portfolioNow, change,
                percent(change, seat.portfolioAtStart), seat.stakeTicker, seat.stakeQuantity, seat.stakePrice, stakeNow);

        return new BlackjackState(true, table, coach, stats, reality);
    }

    /** The trader's cash plus holdings at live prices, across their accounts. */
    private BigDecimal portfolioValue() {
        return accountService.getAccountsForCurrentUser().stream()
                .map(AccountResponse::getAccountId)
                .map(accountService::getPortfolioForAccount)
                .map(p -> p.getTotalBalance())
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<AccountHoldingResponse> holdings() {
        return accountService.getAccountsForCurrentUser().stream()
                .flatMap(a -> accountService.getAccountHoldingsForCurrentUser(a.getAccountId()).stream())
                .toList();
    }

    private Integer userId() {
        Integer userId = securityContextHelper.getUserIdFromContext();
        if (userId == null) {
            throw new InvalidInputException("Sign in to play");
        }
        return userId;
    }

    private void evictIdleSeats() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(IDLE_LIMIT);
        seats.values().removeIf(s -> s.lastActive.isBefore(cutoff));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole == null || whole.signum() == 0 ? BigDecimal.ZERO
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
