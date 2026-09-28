package com.neueda.leap;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.service.OwnershipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class OwnershipServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountRepository accountRepository;

    private OwnershipService ownershipService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ownershipService = new OwnershipService(userRepository, accountRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testRequireAccountAllowsOwnedAccount() {
        setAuthenticatedUser(10);

        Users user = new Users();
        user.setUserId(10);
        user.setClientId(99);

        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(accountRepository.existsByAccountIdAndClientId(123, 99)).thenReturn(true);

        assertDoesNotThrow(() -> ownershipService.requireAccount(123));
    }

    @Test
    void testRequireAccountUserNotFound() {
        setAuthenticatedUser(1);

        when(userRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> ownershipService.requireAccount(1));
    }

    @Test
    void testRequireAccountForbiddenWhenAccountOwnedByDifferentClient() {
        setAuthenticatedUser(1);

        Users user = new Users();
        user.setUserId(1);
        user.setClientId(1);

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(accountRepository.existsByAccountIdAndClientId(1, 1)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> ownershipService.requireAccount(1));
    }

    @Test
    void testRequireAccountForbiddenWhenAuthenticationMissing() {
        assertThrows(ForbiddenException.class, () -> ownershipService.requireAccount(1));
    }

    @Test
    void testRequireAccountForbiddenWhenUserIdMissingFromDetails() {
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken("trader1", null, List.of());
        authentication.setDetails(Map.of("clientId", 1, "username", "trader1"));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        assertThrows(ForbiddenException.class, () -> ownershipService.requireAccount(1));
    }

    @Test
    void testRequireAccountUserMissingClientAssociation() {
        setAuthenticatedUser(5);

        Users user = new Users();
        user.setUserId(5);
        user.setClientId(null);

        when(userRepository.findById(5)).thenReturn(Optional.of(user));

        assertThrows(UserNotFoundException.class, () -> ownershipService.requireAccount(1));
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
