package com.neueda.leap.integration;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.TradeActivityRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.ClientSegmentService;
import com.neueda.leap.service.ReportingLoadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-16: fills are copied into the reporting store, once each, and the internal report reads them from
 * there, for Analysts and Ops only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentsRepository instrumentsRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private TradeActivityRepository tradeActivityRepository;
    @Autowired private ReportingLoadService reportingLoadService;

    // The segment rules use Postgres-only SQL; what matters here is that each trade is tagged with one
    @MockBean private ClientSegmentService clientSegmentService;

    private String ticker;
    private Integer clientId;
    private String analystBearer;
    private String traderBearer;
    private final LocalDateTime yesterday = LocalDateTime.now().minusDays(1).withNano(0);

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Clients client = clientRepository.save(new Clients("Report", "Client", unique + "@example.com"));
        clientId = client.getClientId();
        Users trader = userRepository.save(new Users(clientId, "trader-" + unique, "not-used"));
        traderBearer = "Bearer " + jwtUtil.generateToken(trader);
        Users analyst = new Users(null, "analyst-" + unique, "not-used");
        analyst.setRole("ANALYST");
        analystBearer = "Bearer " + jwtUtil.generateToken(userRepository.save(analyst));
        // Like the real rules, the segment depends on the client's trades already being in the store,
        // so a load that works segments out before storing the new rows tags them wrongly
        when(clientSegmentService.getCurrentSegments()).thenAnswer(inv ->
                tradeActivityRepository.findAll().stream().anyMatch(t -> t.getClientId().equals(clientId))
                        ? Map.of(clientId, "Premier") : Map.of(clientId, "Dormant"));

        Integer accountId = accountRepository.save(new Accounts(clientId, LocalDate.now(), new BigDecimal("10000"))).getAccountId();
        ticker = "R" + unique.toUpperCase();
        Integer instrumentId = instrumentsRepository.save(new Instruments(ticker, "Report instrument", "Equity", "US")).getInstrumentId();

        // Two fills (a buy of 10 at 20 and a sell of 4 at 25) and one rejected order, which isn't trading activity
        fill(accountId, instrumentId, "BUY", "10", "20");
        fill(accountId, instrumentId, "SELL", "4", "25");
        Orders rejected = new Orders(accountId, instrumentId, "BUY", new BigDecimal("1"), new BigDecimal("20"), yesterday.toLocalDate());
        rejected.setOrderStatus("REJECTED");
        rejected.setRejectionReason("Not enough cash");
        orderRepository.save(rejected);
    }

    private void fill(Integer accountId, Integer instrumentId, String side, String quantity, String price) {
        Orders order = new Orders(accountId, instrumentId, side, new BigDecimal(quantity), new BigDecimal(price), yesterday.toLocalDate());
        order.setOrderStatus("FILLED");
        order.setExecutionPrice(new BigDecimal(price));
        order.setExecutedAt(yesterday);
        orderRepository.save(order);
    }

    private long storedRowsForThisTest() {
        return tradeActivityRepository.findAll().stream().filter(t -> t.getTicker().equals(ticker)).count();
    }

    @Test
    void copiesEachFillOnceWithItsSegment() {
        reportingLoadService.load();
        reportingLoadService.load();

        assertEquals(2, storedRowsForThisTest());
        tradeActivityRepository.findAll().stream().filter(t -> t.getTicker().equals(ticker)).forEach(t -> {
            assertEquals("Premier", t.getClientSegment());
            assertEquals(clientId, t.getClientId());
            assertEquals(yesterday.toLocalDate(), t.getTradeDate());
        });
    }

    @Test
    void analystReportsComeFromTheStore() throws Exception {
        reportingLoadService.load();
        String range = "from=" + yesterday.toLocalDate() + "&to=" + LocalDate.now();

        // 10 x 20 + 4 x 25 = 300, from one client, one buy and one sell
        mockMvc.perform(get("/internal/reports/activity?groupBy=instrument&" + range).header("Authorization", analystBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("instrument"))
                .andExpect(jsonPath("$.dataAsOf").exists())
                .andExpect(jsonPath("$.rows[?(@.key == '" + ticker + "')].trades").value(2))
                .andExpect(jsonPath("$.rows[?(@.key == '" + ticker + "')].buys").value(1))
                .andExpect(jsonPath("$.rows[?(@.key == '" + ticker + "')].sells").value(1))
                .andExpect(jsonPath("$.rows[?(@.key == '" + ticker + "')].tradedValue").value(300.0))
                .andExpect(jsonPath("$.rows[?(@.key == '" + ticker + "')].activeClients").value(1));

        // By day, the range is covered day by day, quiet days included
        mockMvc.perform(get("/internal/reports/activity?groupBy=day&segment=Premier&" + range).header("Authorization", analystBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.segment").value("Premier"));

            mockMvc.perform(get("/internal/reports/order-health?" + range).header("Authorization", analystBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.filledOrders").value(2))
                .andExpect(jsonPath("$.rejectedOrders").value(1))
                .andExpect(jsonPath("$.failedOrders").value(0))
                .andExpect(jsonPath("$.successRate").value(0.6667))
                .andExpect(jsonPath("$.failureRate").value(0.3333))
                .andExpect(jsonPath("$.dailyRates.length()").value(2))
                .andExpect(jsonPath("$.dailyRates[0].totalOrders").value(3))
                .andExpect(jsonPath("$.dailyRates[0].successRate").value(0.6667))
                .andExpect(jsonPath("$.dailyRates[1].totalOrders").value(0));
    }

    @Test
    void onlyAnalystsAndOpsCanReadReports() throws Exception {
        mockMvc.perform(get("/internal/reports/activity").header("Authorization", traderBearer))
                .andExpect(status().isForbidden());
            mockMvc.perform(get("/internal/reports/order-health").header("Authorization", traderBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/reports/activity"))
                .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/internal/reports/order-health"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownGroupingIsABadRequest() throws Exception {
        mockMvc.perform(get("/internal/reports/activity?groupBy=hour").header("Authorization", analystBearer))
                .andExpect(status().isBadRequest());
    }
}
