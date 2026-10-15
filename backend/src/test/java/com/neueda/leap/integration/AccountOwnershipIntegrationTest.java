package com.neueda.leap.integration;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traders can only see their own accounts (S2.6), checked through the real filter chain,
 * services and database (H2) rather than mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    private Trader traderA;
    private Trader traderB;

    private record Trader(String bearer, Integer accountId) {
    }

    /** A new client with one trader user and one account. Names are unique so tests never collide. */
    private Trader newTrader(String name) {
        String unique = name + "-" + UUID.randomUUID();
        Clients client = clientRepository.save(new Clients("Test", name, unique + "@example.com"));
        Users user = userRepository.save(new Users(client.getClientId(), unique, "not-used"));
        Accounts account = accountRepository.save(new Accounts(client.getClientId(), LocalDate.now(), new BigDecimal("1000")));
        return new Trader("Bearer " + jwtUtil.generateToken(user), account.getAccountId());
    }

    @BeforeEach
    void setUp() {
        traderA = newTrader("a");
        traderB = newTrader("b");
    }

    @Test
    void traderSeesTheirOwnPortfolio() throws Exception {
        mockMvc.perform(get("/accounts/" + traderA.accountId() + "/portfolio").header("Authorization", traderA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(traderA.accountId()));
    }

    @Test
    void traderCannotSeeAnotherClientsPortfolio() throws Exception {
        mockMvc.perform(get("/accounts/" + traderB.accountId() + "/portfolio").header("Authorization", traderA.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountThatDoesNotExistIsForbiddenToo() throws Exception {
        // The same answer as someone else's account, so account ids can't be probed
        mockMvc.perform(get("/accounts/999999/portfolio").header("Authorization", traderA.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountListHasOnlyTheTradersOwnAccounts() throws Exception {
        mockMvc.perform(get("/accounts").header("Authorization", traderA.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountId").value(traderA.accountId()));
    }
}
