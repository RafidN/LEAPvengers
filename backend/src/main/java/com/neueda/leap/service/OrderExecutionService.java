package com.neueda.leap.service;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.OrderEvents;
import com.neueda.leap.model.Orders;
import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.repository.OrderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Moves ACCEPTED orders on once their delay is up: execute() fills the order if its price still holds,
 * markFailed() records why it could not. trigger() moves a WORKING limit or stop order to ACCEPTED once its
 * price is reached. Kept apart from OrderService so each step runs in its own transaction. Every step locks
 * the order row first, so the fill timer, the working-order monitor and the restart recovery sweep
 * (OrderRecoveryService) can never both act on one order.
 */
@Service
public class OrderExecutionService {
    /** Why an order left SUBMITTED by a crash is closed, in words the client sees in their history. */
    static final String UNCHECKED_REASON =
            "The platform stopped before this order's trading checks finished, so it was not placed. Nothing was bought or sold";

    private final OrderRepository orderRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final AccountRepository accountRepository;
    private final HoldingsRepository holdingsRepository;
    private final CashTransactionRepository cashTransactionRepository;
    private final LatestPriceQuotesRepository latestPriceQuotesRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final TradingRules tradingRules;
    private final MarketHours marketHours;

    public OrderExecutionService(OrderRepository orderRepository, OrderEventsRepository orderEventsRepository,
                                 AccountRepository accountRepository, HoldingsRepository holdingsRepository,
                                 CashTransactionRepository cashTransactionRepository,
                                 LatestPriceQuotesRepository latestPriceQuotesRepository,
                                 InstrumentsRepository instrumentsRepository,
                                 TradingRules tradingRules, MarketHours marketHours) {
        this.orderRepository = orderRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.accountRepository = accountRepository;
        this.holdingsRepository = holdingsRepository;
        this.cashTransactionRepository = cashTransactionRepository;
        this.latestPriceQuotesRepository = latestPriceQuotesRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.tradingRules = tradingRules;
        this.marketHours = marketHours;
    }

    public void markFailed(Integer orderId, String reason) {
        markFailed(orderId, reason, null);
    }

    /**
     * Marks an ACCEPTED order that could not be filled as FAILED, with the reason and the quote the failed
     * fill was priced against, if any. Runs in a new transaction so the failure is recorded even though the
     * fill that threw was rolled back. Orders in any other status are left alone.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Integer orderId, String reason, LatestPriceQuotes quote) {
        Orders order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !"ACCEPTED".equals(order.getOrderStatus())) {
            return;
        }
        String why = reason == null || reason.isBlank() ? "Order could not be executed" : reason;
        order.setOrderStatus("FAILED");
        order.setRejectionReason(why);
        orderRepository.save(order);
        orderEventsRepository.save(new OrderEvents(orderId, null, "ACCEPTED", "FAILED", LocalDateTime.now(), why)
                .pricedFrom(quote));
    }

    /**
     * Closes an order the platform stopped tracking before its trading checks finished, i.e. one still
     * SUBMITTED after a crash. No checks were passed, so it is REJECTED and nothing moves. Returns whether
     * it changed anything (false if the order had already moved on).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rejectUnchecked(Integer orderId) {
        Orders order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !"SUBMITTED".equals(order.getOrderStatus())) {
            return false;
        }
        order.setOrderStatus("REJECTED");
        order.setRejectionReason(UNCHECKED_REASON);
        orderRepository.save(order);
        orderEventsRepository.save(new OrderEvents(orderId, null, "SUBMITTED", "REJECTED", LocalDateTime.now(), UNCHECKED_REASON));
        return true;
    }

    /**
     * Moves a WORKING limit or stop order to ACCEPTED if the latest quote has reached its price and it can be
     * traded on it now: its market is open, the quote is current and the instrument isn't suspended. Otherwise it
     * keeps working, e.g. until the market opens. Returns whether it was triggered, for the caller to fill it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean trigger(Integer orderId) {
        Orders order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !"WORKING".equals(order.getOrderStatus())) {
            return false;
        }
        Instruments instrument = instrumentsRepository.findById(order.getInstrumentId()).orElseThrow();
        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(order.getInstrumentId()).orElse(null);
        try {
            tradingRules.checkTradable(instrument, marketHours.statusOf(instrument));
            tradingRules.checkPriceIsRecent(quote, instrument.getQuoteDelayMinutes());
        } catch (TradingRuleException e) {
            return false;
        }
        if (!TradingRules.isReached(order.getOrderType(), order.getOrderKind(), order.getTriggerPrice(), quote.getPrice())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        order.setOrderStatus("ACCEPTED");
        order.setTriggeredAt(now);
        orderRepository.save(order);
        boolean limit = order.getOrderKind().equals(TradingRules.LIMIT);
        orderEventsRepository.save(new OrderEvents(orderId, null, "WORKING", "ACCEPTED", now,
                "Triggered: the price reached " + TradingRules.money(quote.getPrice()) + " against your "
                        + (limit ? "limit" : "stop") + " of " + TradingRules.money(order.getTriggerPrice()) + ", "
                        + TradingRules.describeQuote(quote, instrument.getQuoteDelayMinutes()))
                .pricedFrom(quote));
        return true;
    }

    /**
     * Fills an ACCEPTED order at the latest price, if the instrument can still be traded on it (BR-08: priced
     * against a current quote at execution). A market order fills only if that is still the price it was accepted
     * at; a price that moved during the delay makes the order out of date, so this throws instead. A triggered
     * limit order fills only at its limit or better, and a triggered stop order at whatever the price now is, as
     * it is there to get out (or in) once the price is reached. Cash, holdings, the cash transaction, the order and
     * its event all change in this one transaction, so if anything throws none of them change (BR-09).
     */
    @Transactional
    public Orders execute(Integer orderId) {
        Orders order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " does not exist"));
        if (!"ACCEPTED".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Order " + orderId + " is " + order.getOrderStatus() + ", only ACCEPTED orders can be filled");
        }
        // Locking the account makes concurrent fills on it wait their turn, so two orders can't both
        // spend the same cash or sell the same shares
        Accounts account = accountRepository.findByIdForUpdate(order.getAccountId())
                .orElseThrow(() -> new IllegalStateException("Account " + order.getAccountId() + " does not exist"));
        Instruments instrument = instrumentsRepository.findById(order.getInstrumentId())
                .orElseThrow(() -> new IllegalStateException("Instrument " + order.getInstrumentId() + " does not exist"));

        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(order.getInstrumentId()).orElse(null);
        Holdings holding = holdingsRepository.findByAccountIdAndInstrumentId(order.getAccountId(), order.getInstrumentId())
                .orElse(null);
        boolean buy = order.getOrderType().equals("BUY");
        try {
            tradingRules.checkTradable(instrument, marketHours.statusOf(instrument));
            tradingRules.checkPriceIsRecent(quote, instrument.getQuoteDelayMinutes());
            switch (order.getOrderKind()) {
                case TradingRules.LIMIT -> tradingRules.checkLimitStillMet(order.getOrderType(), order.getTriggerPrice(), quote);
                case TradingRules.STOP -> { }
                default -> tradingRules.checkPriceUnchanged(order.getPrice(), quote);
            }
            if (buy) {
                // This order is ACCEPTED now, so its own cash is no longer counted as held
                tradingRules.checkSufficientCash(account.getBalance(),
                        orderRepository.cashHeldForWorkingBuys(order.getAccountId(), null), order.getQuantity(), quote.getPrice());
            } else {
                tradingRules.checkSufficientHoldings(holding == null ? BigDecimal.ZERO : holding.getQuantity(), order.getQuantity());
            }
        } catch (TradingRuleException e) {
            throw e.pricedFrom(quote);
        }
        BigDecimal price = quote.getPrice();

        // Money columns hold 4 decimal places
        BigDecimal amount = order.getQuantity().multiply(price).setScale(4, RoundingMode.HALF_UP);
        account.setBalance(buy ? account.getBalance().subtract(amount) : account.getBalance().add(amount));
        accountRepository.save(account);
        BigDecimal heldAfter = updateHolding(holding, order.getAccountId(), order.getInstrumentId(),
                buy ? order.getQuantity() : order.getQuantity().negate());

        LocalDateTime now = LocalDateTime.now();
        CashTransactions payment = new CashTransactions(order.getAccountId(), order.getOrderType(), amount,
                now.toLocalDate(), order.getOrderId());
        payment.setCreatedAt(now);
        cashTransactionRepository.save(payment);

        order.setOrderStatus("FILLED");
        order.setExecutionPrice(price);
        order.setExecutedAt(now);
        Orders filled = orderRepository.save(order);
        // created_by is null: the system made this step, not a person
        orderEventsRepository.save(new OrderEvents(filled.getOrderId(), null, "ACCEPTED", "FILLED", now,
                "Filled at " + TradingRules.money(price) + ", " + TradingRules.describeQuote(quote, instrument.getQuoteDelayMinutes()))
                .pricedFrom(quote));
        if (!buy) {
            cancelSellsLargerThan(heldAfter, filled, instrument);
        }
        return filled;
    }

    /**
     * After a sell, cancels the account's working sell orders of the instrument for more than it now holds. A
     * stop-loss and a take-profit limit on the same shares then act as a pair: whichever fills first cancels the
     * other, rather than leaving it to fail later on shares that are gone.
     */
    private void cancelSellsLargerThan(BigDecimal held, Orders sold, Instruments instrument) {
        for (Integer orderId : orderRepository.findWorkingSellIdsLargerThan(sold.getAccountId(), sold.getInstrumentId(), held)) {
            // One locked elsewhere is being filled or canceled right now; waiting for it while this fill holds the
            // account could deadlock, and if it is a sell it will find the shares gone and fail by itself
            Orders order = orderRepository.findByIdForUpdateSkipLocked(orderId).orElse(null);
            if (order == null || !"WORKING".equals(order.getOrderStatus())) {
                continue;
            }
            String why = "Canceled automatically: order #" + sold.getOrderId() + " sold " + TradingRules.quantity(sold.getQuantity())
                    + " " + instrument.getTicker() + ", leaving " + TradingRules.quantity(held)
                    + ", too few for this order to sell " + TradingRules.quantity(order.getQuantity());
            order.setOrderStatus("CANCELED");
            order.setRejectionReason(why);
            orderRepository.save(order);
            orderEventsRepository.save(new OrderEvents(orderId, null, "WORKING", "CANCELED", LocalDateTime.now(), why));
        }
    }

    /**
     * Applies a filled order to the account's position: a buy adds to it (opening one if needed),
     * a sell takes away from it, and a position sold down to zero is removed. Returns the quantity now held.
     */
    private BigDecimal updateHolding(Holdings holding, Integer accountId, Integer instrumentId, BigDecimal change) {
        if (holding == null) {
            holdingsRepository.save(new Holdings(accountId, instrumentId, change, LocalDate.now()));
            return change;
        }
        BigDecimal newQuantity = holding.getQuantity().add(change);
        if (newQuantity.signum() == 0) {
            holdingsRepository.delete(holding);
            return newQuantity;
        }
        holding.setQuantity(newQuantity);
        holding.setAsOfDate(LocalDate.now());
        holdingsRepository.save(holding);
        return newQuantity;
    }
}
