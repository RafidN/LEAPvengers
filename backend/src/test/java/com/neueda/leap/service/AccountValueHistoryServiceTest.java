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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AccountValueHistoryServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);
    private static final int ACCOUNT = 1;
    private static final int NFLX = 7;

    @Mock private AccountRepository accountRepository;
    @Mock private HoldingsRepository holdingsRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CashTransactionRepository cashTransactionRepository;
    @Mock private PriceQuotesRepository priceQuotesRepository;
    @Mock private CurrentUserService currentUserService;

    private AccountValueHistoryService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Users user = new Users();
        user.setClientId(5);
        when(currentUserService.getCurrentUser()).thenReturn(user);
        Clock clock = Clock.fixed(Instant.from(NOW.atOffset(ZoneOffset.UTC)), ZoneOffset.UTC);
        service = new AccountValueHistoryService(accountRepository, holdingsRepository, orderRepository,
                cashTransactionRepository, priceQuotesRepository, currentUserService, clock);
    }

    /**
     * Today: 900 cash and 2 NFLX at 60. Three days ago 2 NFLX were bought at 50 (costing 100) while NFLX was
     * quoted at 40 before the period and 60 since two days ago.
     */
    private void givenTraderBoughtTwoNflxThreeDaysAgo() {
        when(accountRepository.findAccountResponsesByClientId(5)).thenReturn(List.of(
                new AccountResponse(ACCOUNT, LocalDate.of(2026, 1, 1), new BigDecimal("900"))));
        when(holdingsRepository.findByAccountIdIn(List.of(ACCOUNT))).thenReturn(List.of(
                new Holdings(ACCOUNT, NFLX, new BigDecimal("2"), NOW.toLocalDate())));

        Orders buy = new Orders(ACCOUNT, NFLX, "BUY", new BigDecimal("2"), new BigDecimal("50"), NOW.toLocalDate().minusDays(3));
        buy.setOrderStatus("FILLED");
        buy.setExecutionPrice(new BigDecimal("50"));
        buy.setExecutedAt(NOW.minusDays(3));
        when(orderRepository.findByAccountIdInAndOrderStatusAndExecutedAtAfter(anyCollection(), eq("FILLED"), any()))
                .thenReturn(List.of(buy));
        when(cashTransactionRepository.findByAccountIdInAndOrderIdIsNullAndTxnDateGreaterThanEqual(anyCollection(), any()))
                .thenReturn(List.of());

        when(priceQuotesRepository.findFirstByInstrumentIdAndQuoteTimestampLessThanEqualOrderByQuoteTimestampDesc(eq(NFLX), any()))
                .thenReturn(Optional.of(new PriceQuotes(NFLX, new BigDecimal("40"), 0L, NOW.minusDays(30))));
        when(priceQuotesRepository.findLastQuotePerInterval(anyCollection(), any(), any(), anyDouble()))
                .thenReturn(List.of(new PriceQuotes(NFLX, new BigDecimal("60"), 0L, NOW.minusDays(2))));
    }

    @Test
    void rebuildsValueByUndoingTradesAndUsingThePricesOfTheTime() {
        givenTraderBoughtTwoNflxThreeDaysAgo();

        List<AccountValuePoint> points = service.getValueHistoryForCurrentUser("7d");

        assertEquals(AccountValueHistoryService.POINTS, points.size());
        // A week ago: the 100 spent on NFLX was still cash, and no NFLX was held
        AccountValuePoint first = points.get(0);
        assertEquals(NOW.minusDays(7), first.getTime());
        assertEquals(0, new BigDecimal("1000").compareTo(first.getTotalValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(first.getHoldingsValue()));
        // Now: 900 cash + 2 × 60
        AccountValuePoint last = points.get(points.size() - 1);
        assertEquals(NOW, last.getTime());
        assertEquals(0, new BigDecimal("900").compareTo(last.getCash()));
        assertEquals(0, new BigDecimal("1020").compareTo(last.getTotalValue()));
        // Between the buy and the new quote, NFLX is valued at the older 40: 900 + 80
        assertTrue(points.stream().anyMatch(p -> p.getTotalValue().compareTo(new BigDecimal("980")) == 0));
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i).getTime().isAfter(points.get(i - 1).getTime()));
        }
    }

    @Test
    void periodStartsNoEarlierThanTheFirstAccountOpened() {
        givenTraderBoughtTwoNflxThreeDaysAgo();
        when(accountRepository.findAccountResponsesByClientId(5)).thenReturn(List.of(
                new AccountResponse(ACCOUNT, NOW.toLocalDate().minusDays(10), new BigDecimal("900"))));

        List<AccountValuePoint> points = service.getValueHistoryForCurrentUser("1y");

        assertEquals(NOW.toLocalDate().minusDays(10).atStartOfDay(), points.get(0).getTime());
    }

    @Test
    void withdrawalsAreUndoneLikeDeposits() {
        givenTraderBoughtTwoNflxThreeDaysAgo();
        when(cashTransactionRepository.findByAccountIdInAndOrderIdIsNullAndTxnDateGreaterThanEqual(anyCollection(), any()))
                .thenReturn(List.of(new CashTransactions(ACCOUNT, "WITHDRAWAL", new BigDecimal("500"), NOW.toLocalDate().minusDays(1))));

        List<AccountValuePoint> points = service.getValueHistoryForCurrentUser("7d");

        // Before the withdrawal the 500 was still in the account: 1000 + 500
        assertEquals(0, new BigDecimal("1500").compareTo(points.get(0).getTotalValue()));
    }

    @Test
    void traderWithoutAccountsGetsNoPoints() {
        when(accountRepository.findAccountResponsesByClientId(5)).thenReturn(List.of());

        assertTrue(service.getValueHistoryForCurrentUser(null).isEmpty());
    }

    @Test
    void unknownPeriodIsRejected() {
        assertThrows(InvalidInputException.class, () -> service.getValueHistoryForCurrentUser("2w"));
    }
}
