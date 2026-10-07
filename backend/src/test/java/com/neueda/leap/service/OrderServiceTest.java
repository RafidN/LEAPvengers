package com.neueda.leap.service;

import com.neueda.leap.exception.*;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountHoldingResponse;
import com.neueda.leap.model.dto.AccountPortfolioResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.model.dto.PriceQuoteResult;
import com.neueda.leap.model.dto.InstrumentIdResult;

import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.PortfolioRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;

class OrderServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InstrumentsRepository instrumentsRepository;

    @Mock
    private HoldingsRepository holdingsRepository;

    private OrderService orderService;

    private AccountService accountService;

    private OwnershipService ownershipService;

    private OrderRequest orderRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        CurrentUserService currentUserService = new CurrentUserService(userRepository);
        ownershipService = new OwnershipService(null, null);
        orderService = new OrderService(userRepository, instrumentsRepository, orderRepository, holdingsRepository, accountRepository);
        accountService = new AccountService(accountRepository, currentUserService, portfolioRepository, ownershipService);

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
    @Test
    void testRequireExistingUser() {
        orderRequest = new OrderRequest("NFLX", new BigDecimal(5), "BUY", 67);
        assertThrows(UserNotFoundException.class, () -> orderService.placeOrder(1, orderRequest));
    }
    @Test
    void testRequireNotNullTicker() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        orderRequest = new OrderRequest("", new BigDecimal(5), "BUY", 67);
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, orderRequest));
    }
    @Test
    void testRequirePositiveQuantity() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        orderRequest = new OrderRequest("NFLX", new BigDecimal(-2), "BUY", 67);
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, orderRequest));
    }
    @Test
    void testRequireBuySellOrderType() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        orderRequest = new OrderRequest("NFLX", new BigDecimal(-2), "REIMU", 67);
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, orderRequest));
    }
    @Test
    void testRequireExistingAccountNumber() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        orderRequest = new OrderRequest("NFLX", new BigDecimal(5), "BUY", 67);
        assertThrows(InvalidCredentialsException.class, () -> orderService.placeOrder(1, orderRequest));
    }


    @Test
    void testRequireExistingTicker() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        List<AccountResponse> accountResponses = new ArrayList<>();
        accountResponses.add(new AccountResponse(67, LocalDate.now(), new BigDecimal(750)));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(accountRepository.findAccountResponsesByAccountId(67))
        .thenReturn(accountResponses);
        orderRequest = new OrderRequest("NFLX", new BigDecimal(5), "BUY", 67);
        assertThrows(TickerNotFoundException.class, () -> orderService.placeOrder(1, orderRequest));
    }
    @Test
    void testRequireSufficientBalanceOnBuy() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        List<AccountResponse> accountResponses = new ArrayList<>();
        accountResponses.add(new AccountResponse(67, LocalDate.now(), new BigDecimal(750)));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(accountRepository.findAccountResponsesByAccountId(67)).thenReturn(accountResponses);
        List<PriceQuoteResult> prices = new ArrayList<>();
        List<InstrumentIdResult> instrumentIds = new ArrayList<>();
        prices.add(new PriceQuoteResult("NFLX", "Netflix", new BigDecimal(670), "Equity", LocalDateTime.now()));
        instrumentIds.add(new InstrumentIdResult(1));
        when(instrumentsRepository.searchInstrumentPrice("NFLX")).thenReturn(prices);
        when(instrumentsRepository.searchInstrumentId("NFLX")).thenReturn(instrumentIds);
        orderRequest = new OrderRequest("NFLX", new BigDecimal(5), "BUY", 67);
        assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, orderRequest));
    }
    // @Test
    // void testRequireSufficientHoldingOnSell() {
    //     Users testUser = new Users();
    //     testUser.setUserId(1);
    //     testUser.setClientId(1);
    //     List<AccountResponse> accountResponses = new ArrayList<>();
    //     accountResponses.add(new AccountResponse(67, LocalDate.now(), new BigDecimal(750)));
    //     when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
    //     when(accountRepository.findAccountResponsesByAccountId(67)).thenReturn(accountResponses);
    //     List<PriceQuoteResult> prices = new ArrayList<>();
    //     List<InstrumentIdResult> instrumentIds = new ArrayList<>();
    //     prices.add(new PriceQuoteResult("NFLX", "Netflix", new BigDecimal(6.70), "Equity", LocalDateTime.now()));
    //     instrumentIds.add(new InstrumentIdResult(1));
    //     when(instrumentsRepository.searchInstrumentPrice("NFLX")).thenReturn(prices);
    //     when(instrumentsRepository.searchInstrumentId("NFLX")).thenReturn(instrumentIds);
    //     orderRequest = new OrderRequest("NFLX", new BigDecimal(5), "SELL", 67);
    //     List<TickerSearchResult> instrumentIds = new ArrayList<>();
    //     when(holdingsRepository.searchByInstrumentQuery(testUser.getClientId(), instrumentIds.get(0).getInstrumentId)).thenReturn
    //     assertThrows(InvalidInputException.class, () -> orderService.placeOrder(1, orderRequest));
    // }
    @Test
    void testValidBuy() {
        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        List<AccountResponse> accountResponses = new ArrayList<>();
        accountResponses.add(new AccountResponse(67, LocalDate.now(), new BigDecimal(750)));
        when(userRepository.findById(1)).thenReturn(Optional.of(testUser));
        when(accountRepository.findAccountResponsesByAccountId(67)).thenReturn(accountResponses);
        List<PriceQuoteResult> prices = new ArrayList<>();
        List<InstrumentIdResult> instrumentIds = new ArrayList<>();
        prices.add(new PriceQuoteResult("NFLX", "Netflix", new BigDecimal(6.70), "Equity", LocalDateTime.now()));
        instrumentIds.add(new InstrumentIdResult(1));
        when(instrumentsRepository.searchInstrumentPrice("NFLX")).thenReturn(prices);
        when(instrumentsRepository.searchInstrumentId("NFLX")).thenReturn(instrumentIds);
        Orders order = new Orders(67, instrumentIds.get(0).getInstrumentId(), "BUY", new BigDecimal(67), 
            prices.get(0).getCurrentPrice(), LocalDate.now());
        when(orderRepository.save(any(Orders.class))).thenReturn(order);
        orderRequest = new OrderRequest("NFLX", new BigDecimal(5), "BUY", 67);
        assertDoesNotThrow(() -> orderService.placeOrder(1, orderRequest));
    }
    private void setAuthenticatedUser(Integer userId) {
        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("username", "trader" + userId);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("trader" + userId, null, List.of());
        authentication.setDetails(details);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

  
}