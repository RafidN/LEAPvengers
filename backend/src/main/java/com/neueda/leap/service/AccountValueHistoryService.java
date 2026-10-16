package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.PriceQuotes;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.AccountValuePoint;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.PriceQuotesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The signed-in trader's total account value (cash + holdings) over a period, for the dashboard chart.
 *
 * Nothing stores past values, so they are rebuilt: start from today's cash and holdings, walk back in time
 * undoing each filled order and each deposit or withdrawal, and value the holdings at the prices quoted at
 * each moment. Holdings with no order behind them (seed data) count as held for the whole period.
 */
@Service
public class AccountValueHistoryService {
    /** How many points the chart gets, evenly spaced over the period, the last one being now. */
    static final int POINTS = 60;

    private final AccountRepository accountRepository;
    private final HoldingsRepository holdingsRepository;
    private final OrderRepository orderRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final PriceQuotesRepository priceQuotesRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Autowired
    public AccountValueHistoryService(AccountRepository accountRepository, HoldingsRepository holdingsRepository,
                                      OrderRepository orderRepository, CashTransactionRepository cashTransactionRepository,
                                      PriceQuotesRepository priceQuotesRepository, CurrentUserService currentUserService) {
        // Order and quote times are stored in the database server's local time, so compare in local time too
        this(accountRepository, holdingsRepository, orderRepository, cashTransactionRepository, priceQuotesRepository,
                currentUserService, Clock.systemDefaultZone());
    }

    public AccountValueHistoryService(AccountRepository accountRepository, HoldingsRepository holdingsRepository,
                                      OrderRepository orderRepository, CashTransactionRepository cashTransactionRepository,
                                      PriceQuotesRepository priceQuotesRepository, CurrentUserService currentUserService,
                                      Clock clock) {
        this.accountRepository = accountRepository;
        this.holdingsRepository = holdingsRepository;
        this.orderRepository = orderRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.priceQuotesRepository = priceQuotesRepository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    /** Something that changed cash or holdings at a moment: what it did, so it can be undone. */
    private record Change(LocalDateTime time, BigDecimal cashChange, Integer instrumentId, BigDecimal quantityChange) {
    }

    /**
     * All of the trader's accounts added together, oldest point first. period is 1d, 7d, 1m (default) or 1y;
     * a period reaching back before the first account opened starts on that day instead.
     */
    @Transactional(readOnly = true)
    public List<AccountValuePoint> getValueHistoryForCurrentUser(String period) {
        Duration length = periodLength(period);
        Users user = currentUserService.getCurrentUser();
        List<AccountResponse> accounts = accountRepository.findAccountResponsesByClientId(user.getClientId());
        if (accounts.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime firstOpened = accounts.stream().map(AccountResponse::getOpenedDate)
                .min(Comparator.naturalOrder()).orElseThrow().atStartOfDay();
        LocalDateTime start = now.minus(length).isBefore(firstOpened) ? firstOpened : now.minus(length);
        List<Integer> accountIds = accounts.stream().map(AccountResponse::getAccountId).toList();

        // Today's position
        BigDecimal cash = accounts.stream().map(AccountResponse::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Integer, BigDecimal> quantities = new HashMap<>();
        for (Holdings h : holdingsRepository.findByAccountIdIn(accountIds)) {
            quantities.merge(h.getInstrumentId(), h.getQuantity(), BigDecimal::add);
        }

        List<Change> changes = changesSince(accountIds, start);
        Set<Integer> instrumentIds = new HashSet<>(quantities.keySet());
        changes.stream().map(Change::instrumentId).filter(id -> id != null).forEach(instrumentIds::add);
        long stepNanos = Duration.between(start, now).toNanos() / (POINTS - 1);
        Map<Integer, List<PriceQuotes>> prices = pricesBetween(instrumentIds, start, now, stepNanos);

        // Walk back from now, undoing each change once the point in time is before it
        List<AccountValuePoint> points = new ArrayList<>();
        int next = 0;
        for (int i = POINTS - 1; i >= 0; i--) {
            LocalDateTime t = i == POINTS - 1 ? now : start.plusNanos(stepNanos * i);
            while (next < changes.size() && changes.get(next).time().isAfter(t)) {
                Change change = changes.get(next++);
                cash = cash.subtract(change.cashChange());
                if (change.instrumentId() != null) {
                    quantities.merge(change.instrumentId(), change.quantityChange().negate(), BigDecimal::add);
                }
            }
            BigDecimal holdingsValue = BigDecimal.ZERO;
            for (Map.Entry<Integer, BigDecimal> holding : quantities.entrySet()) {
                if (holding.getValue().signum() != 0) {
                    holdingsValue = holdingsValue.add(holding.getValue().multiply(priceAt(prices.get(holding.getKey()), t)));
                }
            }
            points.add(new AccountValuePoint(t.withNano(0), cash.setScale(2, RoundingMode.HALF_UP),
                    holdingsValue.setScale(2, RoundingMode.HALF_UP)));
        }
        Collections.reverse(points);
        return points;
    }

    /** Filled orders and deposits/withdrawals after start, newest first. */
    private List<Change> changesSince(List<Integer> accountIds, LocalDateTime start) {
        List<Change> changes = new ArrayList<>();
        for (Orders order : orderRepository.findByAccountIdInAndOrderStatusAndExecutedAtAfter(accountIds, "FILLED", start)) {
            // The same amount OrderExecutionService moved: quantity × execution price, to 4 decimal places
            BigDecimal amount = order.getQuantity().multiply(order.getExecutionPrice()).setScale(4, RoundingMode.HALF_UP);
            boolean buy = "BUY".equals(order.getOrderType());
            changes.add(new Change(order.getExecutedAt(), buy ? amount.negate() : amount, order.getInstrumentId(),
                    buy ? order.getQuantity() : order.getQuantity().negate()));
        }
        for (CashTransactions txn : cashTransactionRepository
                .findByAccountIdInAndOrderIdIsNullAndTxnDateGreaterThanEqual(accountIds, start.toLocalDate())) {
            // Rows written before cash movements had a time only have their date; those count from its start
            LocalDateTime time = txn.getCreatedAt() != null ? txn.getCreatedAt() : txn.getTxnDate().atStartOfDay();
            if (time.isAfter(start)) {
                boolean withdrawal = "WITHDRAWAL".equals(txn.getTxnType());
                changes.add(new Change(time, withdrawal ? txn.getAmount().negate() : txn.getAmount(), null, null));
            }
        }
        changes.sort(Comparator.comparing(Change::time).reversed());
        return changes;
    }

    /**
     * Each instrument's quotes over the period, oldest first, led by the last quote before it began.
     * Only the last quote before each point is fetched: the live generator writes one every few seconds,
     * far more than the chart's points need.
     */
    private Map<Integer, List<PriceQuotes>> pricesBetween(Set<Integer> instrumentIds, LocalDateTime start,
                                                          LocalDateTime end, long stepNanos) {
        Map<Integer, List<PriceQuotes>> prices = new HashMap<>();
        for (Integer id : instrumentIds) {
            List<PriceQuotes> quotes = new ArrayList<>();
            priceQuotesRepository.findFirstByInstrumentIdAndQuoteTimestampLessThanEqualOrderByQuoteTimestampDesc(id, start)
                    .ifPresent(quotes::add);
            prices.put(id, quotes);
        }
        if (!instrumentIds.isEmpty()) {
            // A zero-length period (an account opened this instant) has nothing between its ends
            double stepSeconds = Math.max(stepNanos / 1e9, 1);
            for (PriceQuotes q : priceQuotesRepository.findLastQuotePerInterval(instrumentIds, start, end, stepSeconds)) {
                prices.get(q.getInstrumentId()).add(q);
            }
        }
        return prices;
    }

    /** The last price quoted at or before t; before an instrument's first quote, its first quote. */
    static BigDecimal priceAt(List<PriceQuotes> quotes, LocalDateTime t) {
        if (quotes == null || quotes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int low = 0;
        int high = quotes.size() - 1;
        int found = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (quotes.get(mid).getQuoteTimestamp().isAfter(t)) {
                high = mid - 1;
            } else {
                found = mid;
                low = mid + 1;
            }
        }
        return quotes.get(Math.max(found, 0)).getPrice();
    }

    private static Duration periodLength(String period) {
        String p = period == null || period.isBlank() ? "1m" : period.trim().toLowerCase();
        return switch (p) {
            case "1d" -> Duration.ofDays(1);
            case "7d" -> Duration.ofDays(7);
            case "1m" -> Duration.ofDays(30);
            case "1y" -> Duration.ofDays(365);
            default -> throw new InvalidInputException("Unsupported period: " + period + " (use 1d, 7d, 1m or 1y)");
        };
    }
}
