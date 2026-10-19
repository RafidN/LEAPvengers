package com.neueda.leap.integration;

import com.jayway.jsonpath.JsonPath;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Risk Lab blackjack end to end: a trader sits down (with play chips, or pretend-staking a holding), plays a
 * hand, and nothing real moves. Only traders can play.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BlackjackIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentsRepository instrumentsRepository;
    @Autowired private HoldingsRepository holdingsRepository;

    private String trader;
    private String analyst;
    private Integer accountId;
    private Integer instrumentId;
    private String ticker;

    /** A trader with 5,000 cash and 30 shares quoted at 70: 2,100 of stock, 7,100 in all. */
    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Clients client = clientRepository.save(new Clients("Lady", "Luck", unique + "@example.com"));
        accountId = accountRepository.save(new Accounts(client.getClientId(), LocalDate.now(), new BigDecimal("5000"))).getAccountId();
        trader = "Bearer " + jwtUtil.generateToken(userRepository.save(new Users(client.getClientId(), "bj-" + unique, "not-used")));
        Users staff = new Users(null, "bj-an-" + unique, "not-used");
        staff.setRole("ANALYST");
        analyst = "Bearer " + jwtUtil.generateToken(userRepository.save(staff));

        ticker = "B" + unique.toUpperCase();
        instrumentId = instrumentsRepository.save(new Instruments(ticker, "Blackjack test stock", "Equity", "US")).getInstrumentId();
        holdingsRepository.save(new Holdings(accountId, instrumentId, new BigDecimal("30"), LocalDate.now()));
        jdbc.update("INSERT INTO latest_price_quotes (instrument_id, price, quote_timestamp, source) VALUES (?, ?, ?, 'YAHOO_LIVE')",
                instrumentId, new BigDecimal("70"), LocalDateTime.now());
    }

    private BigDecimal shares() {
        return holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId).orElseThrow().getQuantity();
    }

    private BigDecimal cash() {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    @Test
    void sitsDownWithPlayChipsAndPlaysAHandWithoutTouchingTheAccount() throws Exception {
        mockMvc.perform(get("/games/blackjack").header("Authorization", trader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seated").value(false));

        mockMvc.perform(post("/games/blackjack/seat").header("Authorization", trader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.chips").value(1000))
                .andExpect(jsonPath("$.table.canDeal").value(true))
                .andExpect(jsonPath("$.realityCheck.portfolioAtStart").value(7100.0));

        String body = mockMvc.perform(post("/games/blackjack/deal").header("Authorization", trader)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bet\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.table.hands[0].cards.length()").value(2))
                .andExpect(jsonPath("$.table.dealerCards.length()").value(2))
                .andExpect(jsonPath("$.stats.wagered").value(25))
                .andReturn().getResponse().getContentAsString();

        // Play it out: stand until the round is over (a natural may already have ended it)
        boolean inRound = JsonPath.read(body, "$.table.inRound");
        if (inRound) {
            // The dealer's hole card is face down while the player decides, and the coach has a view
            mockMvc.perform(get("/games/blackjack").header("Authorization", trader))
                    .andExpect(jsonPath("$.table.dealerCards[1]").doesNotExist())
                    .andExpect(jsonPath("$.coach.recommended").exists());
        }
        while (inRound) {
            body = mockMvc.perform(post("/games/blackjack/stand").header("Authorization", trader))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            inRound = JsonPath.read(body, "$.table.inRound");
        }
        mockMvc.perform(get("/games/blackjack").header("Authorization", trader))
                .andExpect(jsonPath("$.stats.roundsPlayed").value(1))
                .andExpect(jsonPath("$.table.dealerCards[1]").exists());

        // Play money: the real shares and cash are exactly as they were
        assertEquals(0, new BigDecimal("30").compareTo(shares()));
        assertEquals(0, new BigDecimal("5000").compareTo(cash()));
    }

    @Test
    void pretendStakingAHoldingUsesItsValueAndShowsItInShares() throws Exception {
        mockMvc.perform(post("/games/blackjack/seat").header("Authorization", trader)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stakeTicker\":\"" + ticker.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                // 30 shares x 70 = 2,100 chips; before a hand they buy back exactly the 30 shares
                .andExpect(jsonPath("$.table.chips").value(2100))
                .andExpect(jsonPath("$.realityCheck.stakeTicker").value(ticker))
                .andExpect(jsonPath("$.realityCheck.stakeQuantity").value(30.0))
                .andExpect(jsonPath("$.realityCheck.stakeQuantityNow").value(30.0));

        assertEquals(0, new BigDecimal("30").compareTo(shares()));
    }

    @Test
    void cannotStakeWhatYouDontHold() throws Exception {
        mockMvc.perform(post("/games/blackjack/seat").header("Authorization", trader)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stakeTicker\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You don't hold any NOPE"));
    }

    @Test
    void tableLimitsAreEnforced() throws Exception {
        mockMvc.perform(post("/games/blackjack/seat").header("Authorization", trader)).andExpect(status().isOk());

        mockMvc.perform(post("/games/blackjack/deal").header("Authorization", trader)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bet\":1000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/games/blackjack/hit").header("Authorization", trader))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Deal a hand first"));
    }

    @Test
    void onlyTradersCanPlay() throws Exception {
        mockMvc.perform(post("/games/blackjack/seat").header("Authorization", analyst)).andExpect(status().isForbidden());
        mockMvc.perform(get("/games/blackjack")).andExpect(status().isUnauthorized());
    }
}
