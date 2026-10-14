package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 14, 0);
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final int ACCOUNT_ID = 67;
    private static final int INSTRUMENT_ID = 1;
    private static final int ORDER_ID = 500;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventsRepository orderEventsRepository;

    @Mock
    private InstrumentsRepository instrumentsRepository;

    @Mock
    private LatestPriceQuotesRepository latestPriceQuotesRepository;

    @Mock
    private HoldingsRepository holdingsRepository;

    @Mock
    private OwnershipService ownershipService;

    @Mock
    private OrderExecutionService orderExecutionService;

    // Nothing really waits: tests take the scheduled OrderDelay and run it themselves
    @Mock
    private ScheduledExecutorService executorService;

    private OrderService orderService;

    private Accounts account;

    // The order's status each time it was saved; the same Orders object is saved more than once
    private final List<String> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TradingRules tradingRules = new TradingRules(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), Duration.ofMinutes(5));
        orderService = new OrderService(userRepository, instrumentsRepository, latestPriceQuotesRepository,
                orderRepository, orderEventsRepository, holdingsRepository, accountRepository, ownershipService, tradingRules,
                orderExecutionService, executorService, Duration.ofSeconds(3));

        // User 1 (client 1) owns account 67 with 750 cash; NFLX trades at 6.70, quoted 15 seconds ago
        Users user = new Users();
        user.setUserId(1);
        user.setClientId(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        account = new Accounts(ACCOUNT_ID, 1, LocalDate.of(2025, 1, 1), new BigDecimal("750"));
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(instrumentsRepository.findByTickerIgnoreCase("NFLX"))
                .thenReturn(Optional.of(new Instruments(INSTRUMENT_ID, "NFLX", "Netflix", "Equity", "US")));
        givenQuote("6.70", NOW.minusSeconds(15));
        givenHolding("6");
        // The database assigns the order id on insert
        when(orderRepository.save(any(Orders.class))).thenAnswer(invocation -> {
            Orders order = invocation.getArgument(0);
            order.setOrderId(ORDER_ID);
            savedStatuses.add(order.getOrderStatus());
            return order;
        });
    }

    private void givenQuote(String price, LocalDateTime at) {
        LatestPriceQuotes quote = new LatestPriceQuotes();
        quote.setInstrumentId(INSTRUMENT_ID);
        quote.setPrice(new BigDecimal(price));
        quote.setQuoteTimestamp(at);
        when(latestPriceQuotesRepository.findByInstrumentId(INSTRUMENT_ID)).thenReturn(Optional.of(quote));
    }

    private void givenHolding(String quantity) {
        when(holdingsRepository.findByAccountIdAndInstrumentId(ACCOUNT_ID, INSTRUMENT_ID))
                .thenReturn(Optional.of(new Holdings(ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal(quantity), LocalDate.now())));
    }

    private static OrderRequest order(String ticker, String quantity, String side) {
        return new OrderRequest(ticker, quantity == null ? null : new BigDecimal(quantity), side, ACCOUNT_ID);
    }

    private Orders savedOrder() {
        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository, atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    /** The order's events, checking each was made by the trader for order 500. */
    private List<OrderEvents> savedEvents() {
        ArgumentCaptor<OrderEvents> saved = ArgumentCaptor.forClass(OrderEvents.class);
        verify(orderEventsRepository, atLeastOnce()).save(saved.capture());
        for (OrderEvents event : saved.getAllValues()) {
            assertEquals(ORDER_ID, event.getOrderId());
            assertEquals(1, event.getCreatedBy());
        }
        return saved.getAllValues();
    }

    private static void assertStep(OrderEvents event, String fromStatus, String toStatus, String details) {
        assertEquals(fromStatus, event.getFromStatus());
        assertEquals(toStatus, event.getToStatus());
        assertEquals(details, event.getDetails());
    }

    /** Placing an order never moves cash or holdings; that is OrderExecutionService's job. */
    private void assertCashAndHoldingsUntouched() {
        verify(accountRepository, never()).save(any());
        verify(holdingsRepository, never()).save(any());
        verify(holdingsRepository, never()).delete(any());
    }

    /** The rule failed before there was an order to record, so nothing was saved at all. */
    private void assertFailsUnrecorded(String code, Executable placeOrder) {
        TradingRuleException e = assertThrows(TradingRuleException.class, placeOrder);
        assertEquals(code, e.getCode());
        verify(orderRepository, never()).save(any());
        verify(orderEventsRepository, never()).save(any());
        assertCashAndHoldingsUntouched();
        assertNothingScheduled();
    }

    /** The rule failure is still thrown, and the order is recorded as SUBMITTED then REJECTED, with events saying why. */
    private void assertRecordedAsRejected(String code, Executable placeOrder) {
        TradingRuleException e = assertThrows(TradingRuleException.class, placeOrder);
        assertEquals(code, e.getCode());

        assertEquals(List.of("SUBMITTED", "REJECTED"), savedStatuses);
        Orders order = savedOrder();
        assertEquals(e.getMessage(), order.getRejectionReason());
        assertNull(order.getExecutionPrice());

        List<OrderEvents> events = savedEvents();
        assertEquals(2, events.size());
        assertStep(events.get(0), null, "SUBMITTED", "Order submitted");
        assertStep(events.get(1), "SUBMITTED", "REJECTED", e.getMessage());
        assertCashAndHoldingsUntouched();
        assertNothingScheduled();
    }

    private void assertNothingScheduled() {
        verify(executorService, never()).schedule(any(Runnable.class), anyLong(), any());
    }

    /** The OrderDelay that accepting an order scheduled, checking it waits 3 seconds. */
    private Runnable scheduledOrderDelay() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(executorService).schedule(task.capture(), eq(3000L), eq(TimeUnit.MILLISECONDS));
        return task.getValue();
    }

    // Request checks

    @Test
    void unknownUserIsRejected() {
        assertThrows(UserNotFoundException.class, () -> orderService.placeOrder(2, order("NFLX", "5", "BUY")));
    }

    @Test
    void emptyTickerIsRejected() {
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, order(" ", "5", "BUY")));
    }

    @Test
    void sideOtherThanBuyOrSellIsRejected() {
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, order("NFLX", "5", "REIMU")));
    }

    @Test
    void missingAccountIsRejected() {
        OrderRequest request = new OrderRequest("NFLX", BigDecimal.ONE, "BUY", null);
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, request));
    }

    @Test
    void someoneElsesAccountIsForbidden() {
        doThrow(new ForbiddenException("User does not own the specified account"))
                .when(ownershipService).requireAccount(ACCOUNT_ID);

        assertThrows(ForbiddenException.class, () -> orderService.placeOrder(1, order("NFLX", "5", "BUY")));
        verify(orderRepository, never()).save(any());
    }

    // Rules that fail before there is an order to record

    @Test
    void zeroQuantityIsRejectedWithoutARecord() {
        assertFailsUnrecorded(TradingRules.INVALID_QUANTITY, () -> orderService.placeOrder(1, order("NFLX", "0", "BUY")));
    }

    @Test
    void missingQuantityIsRejectedWithoutARecord() {
        assertFailsUnrecorded(TradingRules.INVALID_QUANTITY, () -> orderService.placeOrder(1, order("NFLX", null, "BUY")));
    }

    @Test
    void unknownTickerIsRejectedWithoutARecord() {
        assertFailsUnrecorded(TradingRules.UNKNOWN_INSTRUMENT, () -> orderService.placeOrder(1, order("NFL", "1", "BUY")));
    }

    @Test
    void instrumentWithNoPriceIsRejectedWithoutARecord() {
        when(latestPriceQuotesRepository.findByInstrumentId(INSTRUMENT_ID)).thenReturn(Optional.empty());
        assertFailsUnrecorded(TradingRules.NO_PRICE, () -> orderService.placeOrder(1, order("NFLX", "1", "BUY")));
    }

    // Rules that reject a real order, which is saved as REJECTED

    @Test
    void stalePriceIsRecordedAsRejected() {
        givenQuote("6.70", NOW.minusHours(1));
        assertRecordedAsRejected(TradingRules.STALE_PRICE, () -> orderService.placeOrder(1, order("NFLX", "1", "BUY")));
    }

    @Test
    void buyCostingMoreThanTheBalanceIsRecordedAsRejected() {
        // 112 x 6.70 = 750.40 > 750
        assertRecordedAsRejected(TradingRules.INSUFFICIENT_CASH, () -> orderService.placeOrder(1, order("NFLX", "112", "BUY")));
    }

    @Test
    void sellOfMoreThanIsHeldIsRecordedAsRejected() {
        // Cost is well within the balance, so only the holdings rule can fail
        assertRecordedAsRejected(TradingRules.INSUFFICIENT_HOLDINGS, () -> orderService.placeOrder(1, order("NFLX", "7", "SELL")));
    }

    @Test
    void sellWithNoHoldingIsRecordedAsRejected() {
        when(holdingsRepository.findByAccountIdAndInstrumentId(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());
        assertRecordedAsRejected(TradingRules.INSUFFICIENT_HOLDINGS, () -> orderService.placeOrder(1, order("NFLX", "1", "SELL")));
    }

    // Accepted orders

    @Test
    void validBuyIsSubmittedThenAcceptedAtTheCheckedPrice() {
        OrderHistoryResult result = orderService.placeOrder(1, order("NFLX", "5", "buy"));

        assertEquals(ORDER_ID, result.getOrderId());
        assertEquals("NFLX", result.getTicker());
        assertEquals("ACCEPTED", result.getOrderStatus());
        assertEquals(ACCOUNT_ID, result.getAccountId());

        assertEquals(List.of("SUBMITTED", "ACCEPTED"), savedStatuses);
        Orders order = savedOrder();
        assertEquals("BUY", order.getOrderType());
        assertEquals(INSTRUMENT_ID, order.getInstrumentId());
        assertEquals(0, new BigDecimal("6.70").compareTo(order.getPrice()));
        assertNull(order.getExecutionPrice());
        assertNull(order.getRejectionReason());
        verify(ownershipService).requireAccount(ACCOUNT_ID);
        assertCashAndHoldingsUntouched();
    }

    @Test
    void acceptedOrderHasSubmittedAndAcceptedEvents() {
        orderService.placeOrder(1, order("NFLX", "5", "BUY"));

        List<OrderEvents> events = savedEvents();
        assertEquals(2, events.size());
        assertStep(events.get(0), null, "SUBMITTED", "Order submitted");
        assertStep(events.get(1), "SUBMITTED", "ACCEPTED", "Passed trading checks");
    }

    @Test
    void validSellIsAcceptedEvenWithALowBalance() {
        account.setBalance(new BigDecimal("1"));

        OrderHistoryResult result = orderService.placeOrder(1, order("NFLX", "6", "SELL"));

        assertEquals("ACCEPTED", result.getOrderStatus());
        assertEquals("SELL", savedOrder().getOrderType());
    }

    @Test
    void fractionalSellWithinHoldingsIsAccepted() {
        givenHolding("0.5");

        assertEquals("ACCEPTED", orderService.placeOrder(1, order("NFLX", "0.5", "SELL")).getOrderStatus());
    }

    // After the delay

    @Test
    void acceptedOrderWaitsThreeSecondsBeforeTheFill() {
        orderService.placeOrder(1, order("NFLX", "5", "BUY"));

        // Nothing happens to the order until the delay is up
        scheduledOrderDelay();
        verify(orderExecutionService, never()).execute(any());
    }

    @Test
    void afterTheDelayTheOrderIsFilled() {
        orderService.placeOrder(1, order("NFLX", "5", "BUY"));

        scheduledOrderDelay().run();

        verify(orderExecutionService).execute(ORDER_ID);
        verify(orderExecutionService, never()).markFailed(any(), any());
    }

    @Test
    void priceThatMovedDuringTheDelayFailsTheOrder() {
        orderService.placeOrder(1, order("NFLX", "5", "BUY"));
        when(orderExecutionService.execute(ORDER_ID))
                .thenThrow(new TradingRuleException(TradingRules.PRICE_CHANGED, "The price moved"));

        scheduledOrderDelay().run();

        verify(orderExecutionService).markFailed(ORDER_ID, "The price moved");
    }

    @Test
    void fillThatThrowsFailsTheOrder() {
        orderService.placeOrder(1, order("NFLX", "5", "BUY"));
        when(orderExecutionService.execute(ORDER_ID)).thenThrow(new IllegalStateException("Database went away"));

        scheduledOrderDelay().run();

        verify(orderExecutionService).markFailed(ORDER_ID, "Database went away");
    }

    @Test
    void failureToMarkFailedDoesNotEscapeTheDelayThread() {
        orderService.placeOrder(1, order("NFLX", "5", "BUY"));
        when(orderExecutionService.execute(ORDER_ID)).thenThrow(new IllegalStateException("Database went away"));
        doThrow(new IllegalStateException("Still away")).when(orderExecutionService).markFailed(eq(ORDER_ID), anyString());

        scheduledOrderDelay().run();
    }
}
