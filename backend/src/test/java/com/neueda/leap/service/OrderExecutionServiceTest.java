package com.neueda.leap.service;

import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.OrderEvents;
import com.neueda.leap.model.Orders;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.repository.OrderRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderExecutionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 14, 0);
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final int ACCOUNT_ID = 67;
    private static final int INSTRUMENT_ID = 1;
    private static final int ORDER_ID = 500;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventsRepository orderEventsRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private HoldingsRepository holdingsRepository;

    @Mock
    private CashTransactionRepository cashTransactionRepository;

    @Mock
    private LatestPriceQuotesRepository latestPriceQuotesRepository;

    private OrderExecutionService orderExecutionService;

    private Accounts account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TradingRules tradingRules = new TradingRules(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), Duration.ofMinutes(5));
        orderExecutionService = new OrderExecutionService(orderRepository, orderEventsRepository, accountRepository,
                holdingsRepository, cashTransactionRepository, latestPriceQuotesRepository, tradingRules);

        // Account 67 has 750 cash and 6 NFLX. Orders were accepted at 6.70, and NFLX is still at 6.70
        account = new Accounts(ACCOUNT_ID, 1, LocalDate.of(2025, 1, 1), new BigDecimal("750"));
        when(accountRepository.findByIdForUpdate(ACCOUNT_ID)).thenReturn(Optional.of(account));
        givenQuote("6.70", NOW.minusSeconds(3));
        givenHolding("6");
        givenAcceptedOrder("BUY", "5");
        when(orderRepository.save(any(Orders.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenAcceptedOrder(String side, String quantity) {
        givenOrder("ACCEPTED", side, quantity);
    }

    /** Order 500 in the given status, accepted at 6.70. */
    private Orders givenOrder(String status, String side, String quantity) {
        Orders order = new Orders(ORDER_ID, ACCOUNT_ID, INSTRUMENT_ID, side, new BigDecimal(quantity), new BigDecimal("6.70"),
                NOW.toLocalDate(), status, null, null, NOW.minusSeconds(1), null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        return order;
    }

    private OrderEvents savedEvent() {
        ArgumentCaptor<OrderEvents> saved = ArgumentCaptor.forClass(OrderEvents.class);
        verify(orderEventsRepository).save(saved.capture());
        return saved.getValue();
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

    /** The holding last written for account 67 / NFLX. */
    private Holdings savedHolding() {
        ArgumentCaptor<Holdings> saved = ArgumentCaptor.forClass(Holdings.class);
        verify(holdingsRepository).save(saved.capture());
        Holdings holding = saved.getValue();
        assertEquals(ACCOUNT_ID, holding.getAccountId());
        assertEquals(INSTRUMENT_ID, holding.getInstrumentId());
        return holding;
    }

    private CashTransactions savedCashTransaction() {
        ArgumentCaptor<CashTransactions> saved = ArgumentCaptor.forClass(CashTransactions.class);
        verify(cashTransactionRepository).save(saved.capture());
        return saved.getValue();
    }

    /** Nothing was written: no cash, holding, cash transaction, order or event change. */
    private void assertNothingChanged() {
        verify(accountRepository, never()).save(any());
        verify(holdingsRepository, never()).save(any());
        verify(holdingsRepository, never()).delete(any());
        verify(cashTransactionRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
        verify(orderEventsRepository, never()).save(any());
    }

    private void assertFailsWithNothingChanged(String code, Executable execute) {
        TradingRuleException e = assertThrows(TradingRuleException.class, execute);
        assertEquals(code, e.getCode());
        assertNothingChanged();
    }

    // Marking an order FAILED (S4.5)

    @Test
    void acceptedOrderThatCouldNotBeFilledIsMarkedFailedWithTheReason() {
        Orders order = givenOrder("ACCEPTED", "BUY", "5");

        orderExecutionService.markFailed(ORDER_ID, "The price moved");

        assertEquals("FAILED", order.getOrderStatus());
        assertEquals("The price moved", order.getRejectionReason());
        assertNull(order.getExecutionPrice());
        verify(orderRepository).save(order);
        OrderEvents event = savedEvent();
        assertEquals(ORDER_ID, event.getOrderId());
        assertNull(event.getCreatedBy());
        assertEquals("ACCEPTED", event.getFromStatus());
        assertEquals("FAILED", event.getToStatus());
        assertEquals("The price moved", event.getDetails());
    }

    @Test
    void failureWithNoMessageStillGetsAReason() {
        // rejection_reason is required for FAILED orders
        Orders order = givenOrder("ACCEPTED", "BUY", "5");

        orderExecutionService.markFailed(ORDER_ID, null);

        assertEquals("Order could not be executed", order.getRejectionReason());
    }

    @Test
    void finishedOrderIsNotMarkedFailed() {
        Orders order = givenOrder("FILLED", "BUY", "5");

        orderExecutionService.markFailed(ORDER_ID, "Too late");

        assertEquals("FILLED", order.getOrderStatus());
        assertNothingChanged();
    }

    // Buys

    @Test
    void buyIsFilledAtTheAcceptedPrice() {
        Orders filled = orderExecutionService.execute(ORDER_ID);

        assertEquals("FILLED", filled.getOrderStatus());
        assertEquals(0, new BigDecimal("6.70").compareTo(filled.getExecutionPrice()));
        assertNotNull(filled.getExecutedAt());
        assertNull(filled.getRejectionReason());
        verify(orderRepository).save(filled);
    }

    @Test
    void buyDebitsCashAndRecordsTheCashTransaction() {
        orderExecutionService.execute(ORDER_ID);

        // 5 x 6.70 = 33.50
        assertEquals(0, new BigDecimal("716.50").compareTo(account.getBalance()));
        verify(accountRepository).save(account);
        CashTransactions cash = savedCashTransaction();
        assertEquals(ACCOUNT_ID, cash.getAccountId());
        assertEquals(ORDER_ID, cash.getOrderId());
        assertEquals("BUY", cash.getTxnType());
        assertEquals(0, new BigDecimal("33.50").compareTo(cash.getAmount()));
        assertEquals(LocalDate.now(), cash.getTxnDate());
    }

    @Test
    void buyAddsToAnExistingHolding() {
        orderExecutionService.execute(ORDER_ID);

        assertEquals(0, new BigDecimal("11").compareTo(savedHolding().getQuantity()));
    }

    @Test
    void firstBuyOpensANewHolding() {
        when(holdingsRepository.findByAccountIdAndInstrumentId(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());

        orderExecutionService.execute(ORDER_ID);

        Holdings holding = savedHolding();
        assertNull(holding.getHoldingId());
        assertEquals(0, new BigDecimal("5").compareTo(holding.getQuantity()));
        assertEquals(LocalDate.now(), holding.getAsOfDate());
    }

    @Test
    void fillGetsASystemEventFromAcceptedToFilled() {
        orderExecutionService.execute(ORDER_ID);

        ArgumentCaptor<OrderEvents> saved = ArgumentCaptor.forClass(OrderEvents.class);
        verify(orderEventsRepository).save(saved.capture());
        OrderEvents event = saved.getValue();
        assertEquals(ORDER_ID, event.getOrderId());
        assertNull(event.getCreatedBy());
        assertEquals("ACCEPTED", event.getFromStatus());
        assertEquals("FILLED", event.getToStatus());
        assertEquals("Filled at $6.70", event.getDetails());
    }

    @Test
    void fillLocksTheAccountRow() {
        orderExecutionService.execute(ORDER_ID);

        verify(accountRepository).findByIdForUpdate(ACCOUNT_ID);
        verify(accountRepository, never()).findById(anyInt());
    }

    @Test
    void priceThatMovedDuringTheDelayChangesNothing() {
        // Accepted at 6.70, now 6.80: the order price is out of date
        givenQuote("6.80", NOW.minusSeconds(1));

        assertFailsWithNothingChanged(TradingRules.PRICE_CHANGED, () -> orderExecutionService.execute(ORDER_ID));
        assertEquals(0, new BigDecimal("750").compareTo(account.getBalance()));
    }

    @Test
    void buyNoLongerAffordableChangesNothing() {
        // Another order spent the cash during the delay: 5 x 6.70 = 33.50 > 30
        account.setBalance(new BigDecimal("30"));

        assertFailsWithNothingChanged(TradingRules.INSUFFICIENT_CASH, () -> orderExecutionService.execute(ORDER_ID));
        assertEquals(0, new BigDecimal("30").compareTo(account.getBalance()));
    }

    // Sells

    @Test
    void sellCreditsCashAndRecordsTheCashTransaction() {
        givenAcceptedOrder("SELL", "4");

        orderExecutionService.execute(ORDER_ID);

        // 4 x 6.70 = 26.80
        assertEquals(0, new BigDecimal("776.80").compareTo(account.getBalance()));
        CashTransactions cash = savedCashTransaction();
        assertEquals("SELL", cash.getTxnType());
        assertEquals(0, new BigDecimal("26.80").compareTo(cash.getAmount()));
    }

    @Test
    void partialSellReducesTheHolding() {
        givenAcceptedOrder("SELL", "4");

        orderExecutionService.execute(ORDER_ID);

        assertEquals(0, new BigDecimal("2").compareTo(savedHolding().getQuantity()));
        verify(holdingsRepository, never()).delete(any());
    }

    @Test
    void sellingTheWholePositionRemovesTheHolding() {
        givenAcceptedOrder("SELL", "6");

        orderExecutionService.execute(ORDER_ID);

        verify(holdingsRepository).delete(any(Holdings.class));
        verify(holdingsRepository, never()).save(any());
    }

    @Test
    void sellOfSharesNoLongerHeldChangesNothing() {
        // Another order sold them between acceptance and fill
        givenAcceptedOrder("SELL", "4");
        givenHolding("3");

        assertFailsWithNothingChanged(TradingRules.INSUFFICIENT_HOLDINGS, () -> orderExecutionService.execute(ORDER_ID));
    }

    // Only accepted orders are filled

    @Test
    void alreadyFilledOrderIsNotFilledAgain() {
        Orders order = new Orders(ORDER_ID, ACCOUNT_ID, INSTRUMENT_ID, "BUY", new BigDecimal("5"), new BigDecimal("6.70"),
                NOW.toLocalDate(), "FILLED", null, new BigDecimal("6.70"), NOW.minusSeconds(1), NOW);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> orderExecutionService.execute(ORDER_ID));
        assertNothingChanged();
    }

    @Test
    void missingOrderIsNotFilled() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> orderExecutionService.execute(ORDER_ID));
        assertNothingChanged();
    }
}
