package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountHoldingResponse;
import com.neueda.leap.model.dto.AccountPortfolioResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.PortfolioRepository;
import com.neueda.leap.repository.UserRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    private StubOwnershipService ownershipService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        CurrentUserService currentUserService = new CurrentUserService(userRepository);
        ownershipService = new StubOwnershipService();
        accountService = new AccountService(accountRepository, currentUserService, portfolioRepository, ownershipService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAccountsForCurrentUserReturnsClientAccounts() {
        setAuthenticatedUser(7);

        Users user = new Users();
        user.setUserId(7);
        user.setClientId(22);

        List<AccountResponse> responses = List.of(
            new AccountResponse(101, LocalDate.of(2024, 1, 15), new BigDecimal("1250.50")),
            new AccountResponse(102, LocalDate.of(2024, 3, 1), new BigDecimal("9800.00"))
        );

        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(accountRepository.findAccountResponsesByClientId(22)).thenReturn(responses);

        List<AccountResponse> result = accountService.getAccountsForCurrentUser();

        assertEquals(2, result.size());
        assertEquals(101, result.get(0).getAccountId());
        assertEquals(LocalDate.of(2024, 1, 15), result.get(0).getOpenedDate());
        assertEquals(new BigDecimal("1250.50"), result.get(0).getBalance());
        verify(accountRepository).findAccountResponsesByClientId(22);
    }

    @Test
    void getAccountsForCurrentUserThrowsWhenUserMissing() {
        setAuthenticatedUser(7);

        when(userRepository.findById(7)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> accountService.getAccountsForCurrentUser());
    }

    @Test
    void getPortfolioForAccountBuildsPortfolioResponse() {
        Integer accountId = 101;
        Accounts account = new Accounts();
        account.setAccountId(accountId);
        account.setBalance(new BigDecimal("1250.50"));

        List<AccountHoldingResponse> holdings = List.of(
            new AccountHoldingResponse(
                "AAPL",
                "Apple Inc.",
                "Equity",
                new BigDecimal("10.0000"),
                new BigDecimal("210.50"),
                new BigDecimal("2105.00"),
                LocalDateTime.of(2026, 9, 28, 12, 30)
            ),
            new AccountHoldingResponse(
                "MSFT",
                "Microsoft Corporation",
                "Equity",
                new BigDecimal("5.0000"),
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                LocalDateTime.of(2026, 9, 28, 12, 35)
            )
        );

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(portfolioRepository.findAccountHoldings(accountId)).thenReturn(holdings);

        AccountPortfolioResponse response = accountService.getPortfolioForAccount(accountId);

        assertEquals(accountId, response.getAccountId());
        assertEquals(new BigDecimal("1250.50"), response.getBalance());
        assertEquals(2, response.getHoldings().size());
        assertEquals(new BigDecimal("3855.50"), response.getTotalBalance());
    }

    @Test
    void getPortfolioForAccountThrowsWhenAccountMissing() {
        Integer accountId = 999;

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> accountService.getPortfolioForAccount(accountId));
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

    private static class StubOwnershipService extends OwnershipService {
        StubOwnershipService() {
            super(null, null);
        }

        @Override
        public void requireAccount(Integer accountId) {
            // no-op for AccountService unit tests
        }
    }
}