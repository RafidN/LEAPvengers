package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.OrderEvents;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.OrderRequest;

import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Takes in orders: saves the order as SUBMITTED, checks the trading rules, then moves it straight to
 * ACCEPTED or REJECTED, with an order_events row for each step. After a short delay, OrderDelay has
 * OrderExecutionService fill the order if its price has not moved, or mark it FAILED. The delay is an
 * in-memory timer, so if the platform stops during it OrderRecoveryService finishes the order instead.
 */
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final UserRepository userRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final LatestPriceQuotesRepository latestPriceQuotesRepository;
    private final HoldingsRepository holdingsRepository;
    private final AccountRepository accountRepository;
    private final OwnershipService ownershipService;
    private final TradingRules tradingRules;
    private final OrderExecutionService orderExecutionService;
    private final MarketHours marketHours;
    private final ScheduledExecutorService executorService;
    private final Duration orderDelay;

    private static final Logger logger = Logger.getLogger(OrderService.class.getName());

    @Autowired
    public OrderService(UserRepository userRepository, InstrumentsRepository instrumentsRepository,
                        LatestPriceQuotesRepository latestPriceQuotesRepository,
                        OrderRepository orderRepository, OrderEventsRepository orderEventsRepository,
                        HoldingsRepository holdingsRepository, AccountRepository accountRepository,
                        OwnershipService ownershipService, TradingRules tradingRules,
                        OrderExecutionService orderExecutionService, MarketHours marketHours,
                        @Value("${leap.trading.order-delay:PT0.5S}") Duration orderDelay) {
        // One shared thread is plenty: each delayed step is a few quick queries
        this(userRepository, instrumentsRepository, latestPriceQuotesRepository, orderRepository, orderEventsRepository,
                holdingsRepository, accountRepository, ownershipService, tradingRules, orderExecutionService, marketHours,
                Executors.newSingleThreadScheduledExecutor(), orderDelay);
    }

    public OrderService(UserRepository userRepository, InstrumentsRepository instrumentsRepository,
                        LatestPriceQuotesRepository latestPriceQuotesRepository,
                        OrderRepository orderRepository, OrderEventsRepository orderEventsRepository,
                        HoldingsRepository holdingsRepository, AccountRepository accountRepository,
                        OwnershipService ownershipService, TradingRules tradingRules,
                        OrderExecutionService orderExecutionService, MarketHours marketHours,
                        ScheduledExecutorService executorService, Duration orderDelay) {
        this.userRepository = userRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.latestPriceQuotesRepository = latestPriceQuotesRepository;
        this.orderRepository = orderRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.holdingsRepository = holdingsRepository;
        this.accountRepository = accountRepository;
        this.ownershipService = ownershipService;
        this.tradingRules = tradingRules;
        this.orderExecutionService = orderExecutionService;
        this.marketHours = marketHours;
        this.executorService = executorService;
        this.orderDelay = orderDelay;
    }

    @PreDestroy
    void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * Returns the saved ACCEPTED order. A rule failure is rethrown after the REJECTED order is saved;
     * noRollbackFor keeps that record when the exception leaves this transaction.
     */
    @Transactional(noRollbackFor = TradingRuleException.class)
    public OrderHistoryResult placeOrder(Integer userId, OrderRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Validate the shape of the request
        if (request.getTicker() == null || request.getTicker().trim().isEmpty()) {
            throw new InvalidInputException("Order ticker cannot be empty");
        }
        if (request.getAccountId() == null) {
            throw new InvalidInputException("Order account cannot be empty");
        }
        String orderType = request.getOrderType() == null ? "" : request.getOrderType().trim().toUpperCase();
        if (!orderType.equals("BUY") && !orderType.equals("SELL")) {
            throw new InvalidInputException("Order type must be BUY or SELL");
        }
        String ticker = request.getTicker().trim();
        BigDecimal quantity = request.getQuantity();
        Integer accountId = request.getAccountId();

        // The account must belong to the caller; a missing account gets the same answer so ids can't be probed
        ownershipService.requireAccount(accountId);

        // Without a valid quantity, a known instrument and a price there is no order to record,
        // so these rules fail the request without saving anything
        tradingRules.checkQuantity(quantity);
        Instruments instrument = instrumentsRepository.findByTickerIgnoreCase(ticker).orElse(null);
        tradingRules.checkInstrumentExists(instrument, ticker);
        LatestPriceQuotes quote = latestPriceQuotesRepository.findByInstrumentId(instrument.getInstrumentId()).orElse(null);
        tradingRules.checkPriceExists(quote);

        Orders order = orderRepository.save(
                new Orders(accountId, instrument.getInstrumentId(), orderType, quantity, quote.getPrice(), LocalDate.now()));
        // Each step records the quote it was priced against (BR-15)
        recordEvent(order, user, null, "Order submitted at " + TradingRules.money(quote.getPrice()) + ", "
                + TradingRules.describeQuote(quote, instrument.getQuoteDelayMinutes()), quote);
        try {
            tradingRules.checkTradable(instrument, marketHours.statusOf(instrument));
            tradingRules.checkPriceIsRecent(quote, instrument.getQuoteDelayMinutes());
            if (orderType.equals("BUY")) {
                Accounts account = accountRepository.findById(accountId).orElseThrow();
                tradingRules.checkSufficientCash(account.getBalance(), quantity, quote.getPrice());
            } else {
                Holdings holding = holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrument.getInstrumentId())
                        .orElse(null);
                tradingRules.checkSufficientHoldings(holding == null ? BigDecimal.ZERO : holding.getQuantity(), quantity);
            }
        } catch (TradingRuleException e) {
            order.setOrderStatus("REJECTED");
            order.setRejectionReason(e.getMessage());
            Orders rejected = orderRepository.save(order);
            recordEvent(rejected, user, "SUBMITTED", e.getMessage(), quote);
            throw e.forOrder(rejected.getOrderId());
        }

        order.setOrderStatus("ACCEPTED");
        Orders accepted = orderRepository.save(order);
        recordEvent(accepted, user, "SUBMITTED", "Passed trading checks", quote);
        scheduleAfterCommit(new OrderDelay(accepted.getOrderId()));

        return new OrderHistoryResult(accepted.getOrderId(), instrument.getTicker(), accepted.getOrderType(),
                accepted.getQuantity(), accepted.getPrice(), accepted.getOrderStatus(), accepted.getOrderDate(),
                accepted.getSubmittedAt(), accepted.getAccountId(), accepted.getRejectionReason(),
                accepted.getExecutionPrice(), accepted.getExecutedAt());
    }

    /** Starts the delay only once the ACCEPTED order is committed, so OrderDelay can always find it. */
    private void scheduleAfterCommit(OrderDelay orderDelayTask) {
        Runnable schedule = () -> executorService.schedule(orderDelayTask, orderDelay.toMillis(), TimeUnit.MILLISECONDS);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule.run();
                }
            });
        } else {
            schedule.run();
        }
    }

    /**
     * Fills an ACCEPTED order if the price is still the one it was accepted at. If the fill throws, it was
     * rolled back, and the order is marked FAILED with the reason. recovered is true when the restart
     * recovery sweep (OrderRecoveryService) is finishing an order whose fill timer was lost, which the
     * reason then says. An order that already moved on is left alone, so calling this twice is harmless.
     */
    public void fillOrFail(Integer orderId, boolean recovered) {
        try {
            orderExecutionService.execute(orderId);
        } catch (Exception e) {
            // An order that is no longer ACCEPTED (e.g. the other of timer and sweep got there first) throws
            // too; markFailed leaves it alone
            String reason = recovered ? "The platform restarted before this order was filled. " + e.getMessage() : e.getMessage();
            try {
                orderExecutionService.markFailed(orderId, reason,
                        e instanceof TradingRuleException rule ? rule.getQuote() : null);
            } catch (Exception markFailedError) {
                logger.log(Level.SEVERE, "Could not mark order " + orderId + " as FAILED", markFailedError);
            }
        }
    }

    /** Runs once the order's delay is up. */
    public class OrderDelay implements Runnable {
        private final Integer orderId;

        public OrderDelay(Integer orderId) {
            this.orderId = orderId;
        }

        @Override
        public void run() {
            fillOrFail(orderId, false);
        }
    }

    /** One step of the order, made by the trader who submitted it, priced against quote. */
    private void recordEvent(Orders order, Users user, String fromStatus, String details, LatestPriceQuotes quote) {
        orderEventsRepository.save(new OrderEvents(order.getOrderId(), user.getUserId(), fromStatus,
                order.getOrderStatus(), LocalDateTime.now(), details).pricedFrom(quote));
    }
}
