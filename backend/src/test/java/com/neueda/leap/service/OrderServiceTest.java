package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountHoldingResponse;
import com.neueda.leap.model.dto.AccountPortfolioResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.OrderRequest;
import com.neueda.leap.model.dto.OrderHistoryResult;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        //ticker, quantity, orderType, accountIdz
        orderRequest = new OrderRequest();

    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }
    @Test
    void getAccountsForCurrentUserThrowsWhenUserMissing() {
        setAuthenticatedUser(7);

        when(userRepository.findById(7)).thenReturn(Optional.empty());

        Users testUser = new Users();
        testUser.setUserId(1);
        testUser.setClientId(1);
        List<OrderHistoryResult> orderHistoryResult = orderService.placeOrder(1, orderRequest);
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