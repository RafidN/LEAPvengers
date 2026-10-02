package com.neueda.leap.controller;

import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.model.dto.AccountHoldingResponse;
import com.neueda.leap.model.dto.AccountPortfolioResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountsControllerTest {

    private MockMvc mockMvc;
    private StubAccountService stubAccountService;

    @BeforeEach
    void setUp() {
        List<AccountResponse> responses = List.of(
            new AccountResponse(101, LocalDate.of(2024, 1, 15), new BigDecimal("1250.50")),
            new AccountResponse(102, LocalDate.of(2024, 3, 1), new BigDecimal("9800.00"))
        );

        List<AccountHoldingResponse> holdings = List.of(
            new AccountHoldingResponse(
                "AAPL",
                "Apple Inc.",
                "Equity",
                new BigDecimal("10.0000"),
                new BigDecimal("210.50"),
                new BigDecimal("2105.00"),
                LocalDateTime.of(2026, 9, 28, 12, 30)
            )
        );
        AccountPortfolioResponse portfolioResponse = new AccountPortfolioResponse(
            101,
            new BigDecimal("1250.50"),
            holdings,
            new BigDecimal("3355.50")
        );

        stubAccountService = new StubAccountService(responses, portfolioResponse);
        AccountsController controller = new AccountsController(stubAccountService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void testGetAccounts() throws Exception {
        mockMvc.perform(get("/accounts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].accountId").value(101))
            .andExpect(jsonPath("$[0].openedDate[0]").value(2024))
            .andExpect(jsonPath("$[0].openedDate[1]").value(1))
            .andExpect(jsonPath("$[0].openedDate[2]").value(15))
            .andExpect(jsonPath("$[0].balance").value(1250.50))
            .andExpect(jsonPath("$[1].accountId").value(102));
    }

    @Test
    void testGetPortfolio() throws Exception {
        mockMvc.perform(get("/accounts/101/portfolio"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(101))
            .andExpect(jsonPath("$.balance").value(1250.50))
            .andExpect(jsonPath("$.totalBalance").value(3355.50))
            .andExpect(jsonPath("$.holdings[0].ticker").value("AAPL"))
            .andExpect(jsonPath("$.holdings[0].instrumentName").value("Apple Inc."))
            .andExpect(jsonPath("$.holdings[0].assetClass").value("Equity"))
            .andExpect(jsonPath("$.holdings[0].quantity").value(10.0000))
            .andExpect(jsonPath("$.holdings[0].latestPrice").value(210.50))
            .andExpect(jsonPath("$.holdings[0].value").value(2105.00));
    }

    private static class StubAccountService extends AccountService {
        private final List<AccountResponse> responses;
        private final AccountPortfolioResponse portfolioResponse;

        StubAccountService(List<AccountResponse> responses, AccountPortfolioResponse portfolioResponse) {
            super(null, null, null, null);
            this.responses = responses;
            this.portfolioResponse = portfolioResponse;
        }

        @Override
        public List<AccountResponse> getAccountsForCurrentUser() {
            return responses;
        }

        @Override
        public AccountPortfolioResponse getPortfolioForAccount(Integer accountId) {
            return portfolioResponse;
        }
    }
}