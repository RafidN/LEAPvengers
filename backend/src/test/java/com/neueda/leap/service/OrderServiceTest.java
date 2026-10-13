package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.LatestPriceQuotesRepository;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 14, 0);
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final int ACCOUNT_ID = 67;
    private static final int INSTRUMENT_ID = 1;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InstrumentsRepository instrumentsRepository;

    @Mock
    private LatestPriceQuotesRepository latestPriceQuotesRepository;

    @Mock
    private HoldingsRepository holdingsRepository;

    private OrderService orderService;

    private Accounts account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TradingRules tradingRules = new TradingRules(Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), Duration.ofMinutes(5));
        orderService = new OrderService(userRepository, instrumentsRepository, latestPriceQuotesRepository,
                orderRepository, holdingsRepository, accountRepository, tradingRules);

        // Client 1 owns account 67 with 750 cash; NFLX trades at 6.70, quoted 15 seconds ago
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
        when(orderRepository.save(any(Orders.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    /** The order is rejected by the given rule, and neither cash nor orders were touched. */
    private void assertRejectedBy(String code, Executable placeOrder) {
        TradingRuleException e = assertThrows(TradingRuleException.class, placeOrder);
        assertEquals(code, e.getCode());
        verify(orderRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
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
        account.setClientId(2);
        assertThrows(ForbiddenException.class, () -> orderService.placeOrder(1, order("NFLX", "5", "BUY")));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void nonexistentAccountIsForbidden() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());
        assertThrows(ForbiddenException.class, () -> orderService.placeOrder(1, order("NFLX", "5", "BUY")));
    }

    // Each trading rule, applied by the service

    @Test
    void zeroQuantityIsRejected() {
        assertRejectedBy(TradingRules.INVALID_QUANTITY, () -> orderService.placeOrder(1, order("NFLX", "0", "BUY")));
    }

    @Test
    void missingQuantityIsRejected() {
        assertRejectedBy(TradingRules.INVALID_QUANTITY, () -> orderService.placeOrder(1, order("NFLX", null, "BUY")));
    }

    @Test
    void unknownTickerIsRejected() {
        assertRejectedBy(TradingRules.UNKNOWN_INSTRUMENT, () -> orderService.placeOrder(1, order("NFL", "1", "BUY")));
    }

    @Test
    void stalePriceIsRejected() {
        givenQuote("6.70", NOW.minusHours(1));
        assertRejectedBy(TradingRules.STALE_PRICE, () -> orderService.placeOrder(1, order("NFLX", "1", "BUY")));
    }

    @Test
    void buyCostingMoreThanTheBalanceIsRejected() {
        // 112 x 6.70 = 750.40 > 750
        assertRejectedBy(TradingRules.INSUFFICIENT_CASH, () -> orderService.placeOrder(1, order("NFLX", "112", "BUY")));
    }

    @Test
    void sellOfMoreThanIsHeldIsRejected() {
        // Cost is well within the balance, so only the holdings rule can fail
        assertRejectedBy(TradingRules.INSUFFICIENT_HOLDINGS, () -> orderService.placeOrder(1, order("NFLX", "7", "SELL")));
    }

    @Test
    void sellWithNoHoldingIsRejected() {
        when(holdingsRepository.findByAccountIdAndInstrumentId(ACCOUNT_ID, INSTRUMENT_ID)).thenReturn(Optional.empty());
        assertRejectedBy(TradingRules.INSUFFICIENT_HOLDINGS, () -> orderService.placeOrder(1, order("NFLX", "1", "SELL")));
    }

    // Successful orders

    @Test
    void validBuyIsFilledAndDebitsCash() {
        orderService.placeOrder(1, order("NFLX", "5", "buy"));

        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository).save(saved.capture());
        Orders order = saved.getValue();
        assertEquals("BUY", order.getOrderType());
        assertEquals("FILLED", order.getOrderStatus());
        assertEquals(INSTRUMENT_ID, order.getInstrumentId());
        assertEquals(0, new BigDecimal("6.70").compareTo(order.getExecutionPrice()));
        assertEquals(0, new BigDecimal("716.50").compareTo(account.getBalance()));
        verify(accountRepository).save(account);
    }

    @Test
    void validSellIsFilledAndCreditsCashEvenWithALowBalance() {
        account.setBalance(new BigDecimal("1"));

        orderService.placeOrder(1, order("NFLX", "6", "SELL"));

        ArgumentCaptor<Orders> saved = ArgumentCaptor.forClass(Orders.class);
        verify(orderRepository).save(saved.capture());
        assertEquals("SELL", saved.getValue().getOrderType());
        assertEquals("FILLED", saved.getValue().getOrderStatus());
        assertEquals(0, new BigDecimal("41.20").compareTo(account.getBalance()));
    }

    @Test
    void fractionalSellWithinHoldingsIsFilled() {
        givenHolding("0.5");

        orderService.placeOrder(1, order("NFLX", "0.5", "SELL"));

        verify(orderRepository).save(any(Orders.class));
    }
}
