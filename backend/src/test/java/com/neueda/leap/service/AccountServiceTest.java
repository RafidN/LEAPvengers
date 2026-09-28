package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.repository.AccountRepository;
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

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        CurrentUserService currentUserService = new CurrentUserService(userRepository);
        accountService = new AccountService(accountRepository, currentUserService);
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