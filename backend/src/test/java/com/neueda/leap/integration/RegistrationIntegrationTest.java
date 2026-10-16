package com.neueda.leap.integration;

import com.jayway.jsonpath.JsonPath;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The spec's first success criterion end to end: a new client registers, gets a funded trading account,
 * and can place an order that fills. Plus the ways a registration can be turned down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @Autowired
    private OrderRepository orderRepository;

    private String unique;

    @BeforeEach
    void setUp() {
        unique = UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions register(String email, String username, String password) throws Exception {
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName":" Jane ","lastName":"Doe","email":"%s","username":"%s","password":"%s"}
                """.formatted(email, username, password)));
    }

    private int clientsWithEmail(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM clients WHERE LOWER(email) = LOWER(?)", Integer.class, email);
    }

    @Test
    void newClientGetsAFundedAccountAndCanTrade() throws Exception {
        String body = register("Jane." + unique + "@Example.com", "jane_" + unique, "Secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TRADER"))
                .andExpect(jsonPath("$.email").value("jane." + unique + "@example.com"))
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + JsonPath.read(body, "$.token");

        // One account, opened with the configured deposit, and the deposit is on record
        String accounts = mockMvc.perform(get("/accounts").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].balance").value(100000))
                .andReturn().getResponse().getContentAsString();
        Integer accountId = JsonPath.read(accounts, "$[0].accountId");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cash_transactions WHERE account_id = ? AND txn_type = 'DEPOSIT' AND amount = 100000",
                Integer.class, accountId));
        // The name was stored without the stray spaces
        assertEquals("Jane", jdbc.queryForObject(
                "SELECT first_name FROM clients WHERE email = ?", String.class, "jane." + unique + "@example.com"));

        // ...and the new trader can place an order straight away, which fills
        String ticker = "R" + unique.toUpperCase();
        Integer instrumentId = instrumentsRepository.save(new Instruments(ticker, "Test instrument", "Equity", "US")).getInstrumentId();
        jdbc.update("INSERT INTO latest_price_quotes (instrument_id, price, quote_timestamp) VALUES (?, ?, ?)",
                instrumentId, new BigDecimal("25"), LocalDateTime.now());
        String order = mockMvc.perform(post("/orders").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticker\":\"" + ticker + "\",\"quantity\":4,\"orderType\":\"BUY\",\"accountId\":" + accountId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer orderId = JsonPath.read(order, "$[0].orderId");
        assertEquals("FILLED", waitForFinalStatus(orderId));
        mockMvc.perform(get("/accounts").header("Authorization", bearer))
                .andExpect(jsonPath("$[0].balance").value(99900));
    }

    @Test
    void emailAlreadyRegisteredInAnyCaseIsTurnedDown() throws Exception {
        String email = "taken." + unique + "@example.com";
        register(email, "first_" + unique, "Secret123").andExpect(status().isCreated());

        register(email.toUpperCase(), "second_" + unique, "Secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));
        assertEquals(1, clientsWithEmail(email));
    }

    @Test
    void usernameAlreadyTakenInAnyCaseIsTurnedDown() throws Exception {
        register("a." + unique + "@example.com", "Sam_" + unique, "Secret123").andExpect(status().isCreated());

        register("b." + unique + "@example.com", "sam_" + unique, "Secret123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That username is already taken"));
        assertEquals(0, clientsWithEmail("b." + unique + "@example.com"));
    }

    @Test
    void weakPasswordCreatesNothing() throws Exception {
        String email = "weak." + unique + "@example.com";

        register(email, "weak_" + unique, "short1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwords need 8 to 72 characters, including at least one letter and one number"));
        assertEquals(0, clientsWithEmail(email));
    }

    private String waitForFinalStatus(Integer orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        String status = orderRepository.findById(orderId).orElseThrow().getOrderStatus();
        while (System.currentTimeMillis() < deadline && "ACCEPTED".equals(status)) {
            Thread.sleep(50);
            status = orderRepository.findById(orderId).orElseThrow().getOrderStatus();
        }
        return status;
    }
}
