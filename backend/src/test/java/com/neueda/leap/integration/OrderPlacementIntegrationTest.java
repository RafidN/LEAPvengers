package com.neueda.leap.integration;

import com.jayway.jsonpath.JsonPath;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Placing and executing orders end to end (S4.7): POST /orders through the real filter chain,
 * services, transactions and database (H2), including the delayed fill. The test profile shortens
 * the delay so these tests only wait a moment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPlacementIntegrationTest {

    private static final BigDecimal STARTING_CASH = new BigDecimal("10000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @Autowired
    private HoldingsRepository holdingsRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventsRepository orderEventsRepository;

    // Real repository unless a test makes it throw, to force a failure part way through a fill
    @SpyBean
    private CashTransactionRepository cashTransactionRepository;

    private String bearer;
    private Integer accountId;
    private Integer instrumentId;
    private String ticker;

    /** A new trader with 10,000 cash, and a new instrument priced at 50. Unique each time so tests never collide. */
    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Clients client = clientRepository.save(new Clients("Test", "Trader", unique + "@example.com"));
        Users user = userRepository.save(new Users(client.getClientId(), "trader-" + unique, "not-used"));
        accountId = accountRepository.save(new Accounts(client.getClientId(), LocalDate.now(), STARTING_CASH)).getAccountId();
        bearer = "Bearer " + jwtUtil.generateToken(user);

        ticker = "T" + unique.toUpperCase();
        instrumentId = instrumentsRepository.save(new Instruments(ticker, "Test instrument", "Equity", "US")).getInstrumentId();
        // latest_price_quotes is a read-only view in the app, so the test writes it directly
        jdbc.update("INSERT INTO latest_price_quotes (instrument_id, price, quote_timestamp) VALUES (?, ?, ?)",
                instrumentId, new BigDecimal("50"), LocalDateTime.now());
    }

    private ResultActions placeOrder(String side, String quantity) throws Exception {
        return mockMvc.perform(post("/orders").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticker\":\"" + ticker + "\",\"quantity\":" + quantity + ",\"orderType\":\"" + side
                        + "\",\"accountId\":" + accountId + "}"));
    }

    /** Places an order that passes the rules, and returns its id once the delayed fill has finished. */
    private Integer placeAcceptedOrderAndWait(String side, String quantity) throws Exception {
        String body = placeOrder(side, quantity)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderStatus").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();
        Integer orderId = JsonPath.read(body, "$[0].orderId");
        waitUntilNotAccepted(orderId);
        return orderId;
    }

    private Orders waitUntilNotAccepted(Integer orderId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Orders order = orderRepository.findById(orderId).orElseThrow();
            if (!"ACCEPTED".equals(order.getOrderStatus())) {
                return order;
            }
            Thread.sleep(50);
        }
        return fail("Order " + orderId + " was still ACCEPTED after 10 seconds");
    }

    private Orders theOnlyOrder() {
        List<Orders> orders = orderRepository.findAll().stream()
                .filter(o -> o.getAccountId().equals(accountId))
                .toList();
        assertEquals(1, orders.size());
        return orders.get(0);
    }

    private void assertSteps(Integer orderId, String... toStatuses) {
        List<String> steps = orderEventsRepository.findAuditOrderEventsByOrderId(orderId).stream()
                .map(OrderEventResult::getToStatus)
                .toList();
        assertEquals(List.of(toStatuses), steps);
    }

    private BigDecimal cash() {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private Optional<Holdings> holding() {
        return holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId);
    }

    private List<CashTransactions> cashTransactionsFor(Integer orderId) {
        return cashTransactionRepository.findAll().stream()
                .filter(ct -> orderId.equals(ct.getOrderId()))
                .toList();
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void buyMovesCashDownAndHoldingsUp() throws Exception {
        Integer orderId = placeAcceptedOrderAndWait("BUY", "10");

        Orders order = orderRepository.findById(orderId).orElseThrow();
        assertEquals("FILLED", order.getOrderStatus());
        assertAmount("50", order.getExecutionPrice());
        // 10 x 50 = 500
        assertAmount("9500", cash());
        assertAmount("10", holding().orElseThrow().getQuantity());
        List<CashTransactions> cash = cashTransactionsFor(orderId);
        assertEquals(1, cash.size());
        assertEquals("BUY", cash.get(0).getTxnType());
        assertAmount("500", cash.get(0).getAmount());
        assertSteps(orderId, "SUBMITTED", "ACCEPTED", "FILLED");
    }

    @Test
    void sellMovesCashUpAndHoldingsDown() throws Exception {
        holdingsRepository.save(new Holdings(accountId, instrumentId, new BigDecimal("10"), LocalDate.now()));

        Integer orderId = placeAcceptedOrderAndWait("SELL", "4");

        assertEquals("FILLED", orderRepository.findById(orderId).orElseThrow().getOrderStatus());
        // 4 x 50 = 200
        assertAmount("10200", cash());
        assertAmount("6", holding().orElseThrow().getQuantity());
        assertEquals("SELL", cashTransactionsFor(orderId).get(0).getTxnType());
    }

    @Test
    void tooLargeBuyChangesNothingAndRecordsRejected() throws Exception {
        // 1000 x 50 = 50,000 > 10,000
        placeOrder("BUY", "1000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"));

        Orders order = theOnlyOrder();
        assertEquals("REJECTED", order.getOrderStatus());
        assertTrue(order.getRejectionReason().contains("costs"));
        assertAmount("10000", cash());
        assertTrue(holding().isEmpty());
        assertTrue(cashTransactionsFor(order.getOrderId()).isEmpty());
        assertSteps(order.getOrderId(), "SUBMITTED", "REJECTED");
    }

    @Test
    void sellOfSharesNotOwnedIsRejected() throws Exception {
        placeOrder("SELL", "1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_HOLDINGS"));

        Orders order = theOnlyOrder();
        assertEquals("REJECTED", order.getOrderStatus());
        assertAmount("10000", cash());
        assertSteps(order.getOrderId(), "SUBMITTED", "REJECTED");
    }

    @Test
    void priceThatMovesDuringTheDelayFailsTheOrderAndChangesNothing() throws Exception {
        String body = placeOrder("BUY", "10").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer orderId = JsonPath.read(body, "$[0].orderId");
        jdbc.update("UPDATE latest_price_quotes SET price = ? WHERE instrument_id = ?", new BigDecimal("51"), instrumentId);

        Orders order = waitUntilNotAccepted(orderId);

        assertEquals("FAILED", order.getOrderStatus());
        assertTrue(order.getRejectionReason().contains("out of date"), order.getRejectionReason());
        assertAmount("10000", cash());
        assertTrue(holding().isEmpty());
        assertTrue(cashTransactionsFor(orderId).isEmpty());
        assertSteps(orderId, "SUBMITTED", "ACCEPTED", "FAILED");
    }

    @Test
    void forcedExecutionFailureLeavesOrderFailedAndNothingElseChanged() throws Exception {
        // The fill updates cash and holdings before it writes the cash transaction, so throwing there
        // proves the earlier writes are rolled back with it
        doThrow(new IllegalStateException("Simulated database failure")).when(cashTransactionRepository).save(any());

        Integer orderId = placeAcceptedOrderAndWait("BUY", "10");

        Orders order = orderRepository.findById(orderId).orElseThrow();
        assertEquals("FAILED", order.getOrderStatus());
        assertEquals("Simulated database failure", order.getRejectionReason());
        assertAmount("10000", cash());
        assertTrue(holding().isEmpty());
        assertSteps(orderId, "SUBMITTED", "ACCEPTED", "FAILED");
    }
}
