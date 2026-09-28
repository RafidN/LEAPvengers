package com.neueda.leap.controller;

import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountsControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        List<AccountResponse> responses = List.of(
            new AccountResponse(101, LocalDate.of(2024, 1, 15), new BigDecimal("1250.50")),
            new AccountResponse(102, LocalDate.of(2024, 3, 1), new BigDecimal("9800.00"))
        );

        AccountsController controller = new AccountsController(new StubAccountService(responses));
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

    private static class StubAccountService extends AccountService {
        private final List<AccountResponse> responses;

        StubAccountService(List<AccountResponse> responses) {
            super(null, null);
            this.responses = responses;
        }

        @Override
        public List<AccountResponse> getAccountsForCurrentUser() {
            return responses;
        }
    }
}