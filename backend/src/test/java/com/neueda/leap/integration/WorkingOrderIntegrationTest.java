package com.neueda.leap.integration;

import com.jayway.jsonpath.JsonPath;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.HoldingsRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import com.neueda.leap.service.WorkingOrderService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Limit and stop orders end to end: placed through POST /orders as WORKING, moved (PUT /orders/{id}) and canceled
 * (POST /orders/{id}/cancel), and triggered and filled by WorkingOrderService when the price reaches them. The
 * test profile switches the scheduler off, so each test moves the price and runs the check itself.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkingOrderIntegrationTest {

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

    @Autowired
    private WorkingOrderService workingOrderService;

    private String bearer;
    private Integer accountId;
    private Integer instrumentId;
    private String ticker;

    /** A new trader with 10,000 cash, and a new instrument priced at 50. */
    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Trader trader = newTrader(unique);
        bearer = trader.bearer();
        accountId = trader.accountId();

        ticker = "W" + unique.toUpperCase();
        instrumentId = instrumentsRepository.save(new Instruments(ticker, "Test instrument", "Equity", "US")).getInstrumentId();
        jdbc.update("INSERT INTO latest_price_quotes (instrument_id, price, quote_timestamp) VALUES (?, ?, ?)",
                instrumentId, new BigDecimal("50"), LocalDateTime.now());
    }

    private record Trader(String bearer, Integer accountId) {
    }

    private Trader newTrader(String unique) {
        Clients client = clientRepository.save(new Clients("Test", "Trader", unique + "@example.com"));
        Users user = userRepository.save(new Users(client.getClientId(), "trader-" + unique, "not-used"));
        Accounts account = accountRepository.save(new Accounts(client.getClientId(), LocalDate.now(), new BigDecimal("10000")));
        return new Trader("Bearer " + jwtUtil.generateToken(user), account.getAccountId());
    }

    private void priceMovesTo(String price) {
        jdbc.update("UPDATE latest_price_quotes SET price = ?, quote_timestamp = ? WHERE instrument_id = ?",
                new BigDecimal(price), LocalDateTime.now(), instrumentId);
    }

    private void hold(String quantity) {
        holdingsRepository.save(new Holdings(accountId, instrumentId, new BigDecimal(quantity), LocalDate.now()));
    }

    private ResultActions placeOrder(String side, String quantity, String kind, String triggerPrice) throws Exception {
        String trigger = triggerPrice == null ? "" : ",\"triggerPrice\":" + triggerPrice;
        return mockMvc.perform(post("/orders").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticker\":\"" + ticker + "\",\"quantity\":" + quantity + ",\"orderType\":\"" + side
                        + "\",\"accountId\":" + accountId + ",\"orderKind\":\"" + kind + "\"" + trigger + "}"));
    }

    private Integer placeWorking(String side, String quantity, String kind, String triggerPrice) throws Exception {
        String body = placeOrder(side, quantity, kind, triggerPrice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderStatus").value("WORKING"))
                .andExpect(jsonPath("$[0].orderKind").value(kind))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].orderId");
    }

    private ResultActions cancel(Integer orderId) throws Exception {
        return mockMvc.perform(post("/orders/" + orderId + "/cancel").header("Authorization", bearer));
    }

    private ResultActions move(Integer orderId, String triggerPrice) throws Exception {
        return mockMvc.perform(put("/orders/" + orderId).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"triggerPrice\":" + triggerPrice + "}"));
    }

    private Orders order(Integer orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private List<String> steps(Integer orderId) {
        return orderEventsRepository.findAuditOrderEventsByOrderId(orderId).stream()
                .map(OrderEventResult::getToStatus)
                .toList();
    }

    private BigDecimal cash() {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private BigDecimal held() {
        return holdingsRepository.findByAccountIdAndInstrumentId(accountId, instrumentId)
                .map(Holdings::getQuantity).orElse(BigDecimal.ZERO);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void limitBuyWaitsThenFillsAtTheMarketPriceOnceItFallsToTheLimit() throws Exception {
        Integer orderId = placeWorking("BUY", "10", "LIMIT", "48");

        // Not there yet: nothing moves
        priceMovesTo("48.50");
        assertEquals(0, workingOrderService.fillOrdersAtTriggerPrice());
        assertEquals("WORKING", order(orderId).getOrderStatus());
        assertAmount("10000", cash());

        // Through the limit: fills at the market price, which is better than the limit
        priceMovesTo("47.25");
        assertEquals(1, workingOrderService.fillOrdersAtTriggerPrice());

        Orders filled = order(orderId);
        assertEquals("FILLED", filled.getOrderStatus());
        assertAmount("47.25", filled.getExecutionPrice());
        assertTrue(filled.getTriggeredAt() != null);
        assertAmount("9527.5", cash());
        assertAmount("10", held());
        assertEquals(List.of("SUBMITTED", "WORKING", "ACCEPTED", "FILLED"), steps(orderId));
    }

    @Test
    void stopLossSellsAtTheMarketPriceOnceThePriceFallsToTheStop() throws Exception {
        hold("10");
        Integer orderId = placeWorking("SELL", "10", "STOP", "45");

        priceMovesTo("46");
        assertEquals(0, workingOrderService.fillOrdersAtTriggerPrice());

        // Gapped down through the stop: a stop sells at the price there is, to get out
        priceMovesTo("43");
        assertEquals(1, workingOrderService.fillOrdersAtTriggerPrice());

        Orders filled = order(orderId);
        assertEquals("FILLED", filled.getOrderStatus());
        assertAmount("43", filled.getExecutionPrice());
        assertAmount("10430", cash());
        assertAmount("0", held());
    }

    @Test
    void aPriceTheMarketIsAlreadyAtIsRejectedWithWhichSideToPutItOn() throws Exception {
        hold("10");
        placeOrder("BUY", "1", "LIMIT", "50")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRICE_ALREADY_REACHED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("below the current price of $50.00")));
        placeOrder("SELL", "1", "STOP", "51")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRICE_ALREADY_REACHED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("below the current price")));
        placeOrder("SELL", "1", "LIMIT", "49")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("above the current price")));
    }

    @Test
    void aLimitOrStopOrderNeedsItsPriceAndAMarketOrderTakesNone() throws Exception {
        placeOrder("BUY", "1", "LIMIT", null).andExpect(status().isBadRequest());
        placeOrder("BUY", "1", "LIMIT", "-1").andExpect(status().isBadRequest());
        placeOrder("BUY", "1", "MARKET", "40").andExpect(status().isBadRequest());
        placeOrder("BUY", "1", "TRAILING", "40").andExpect(status().isBadRequest());
        assertTrue(orderRepository.findAll().stream().noneMatch(o -> o.getAccountId().equals(accountId)));
    }

    @Test
    void workingBuysHoldTheirCashSoItCantBeSpentTwice() throws Exception {
        // 150 x 40 = 6,000 held of the 10,000
        placeWorking("BUY", "150", "LIMIT", "40");
        mockMvc.perform(get("/accounts/" + accountId + "/portfolio").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashHeldForOrders").value(6000.0));

        // 100 x 50 = 5,000 is more than the 4,000 left
        placeOrder("BUY", "100", "MARKET", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("held for working buy orders")));
        placeOrder("BUY", "100", "LIMIT", "45")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"));
    }

    @Test
    void aCanceledOrderNeverFills() throws Exception {
        Integer orderId = placeWorking("BUY", "10", "LIMIT", "48");

        cancel(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELED"))
                .andExpect(jsonPath("$.rejectionReason").value("Canceled by you"));
        cancel(orderId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NOT_WORKING"));

        priceMovesTo("40");
        assertEquals(0, workingOrderService.fillOrdersAtTriggerPrice());
        assertEquals("CANCELED", order(orderId).getOrderStatus());
        assertAmount("10000", cash());
        assertEquals(List.of("SUBMITTED", "WORKING", "CANCELED"), steps(orderId));
    }

    @Test
    void movingAnOrderCancelsItAndPlacesItAgainAtTheNewPrice() throws Exception {
        Integer orderId = placeWorking("BUY", "10", "LIMIT", "48");

        String body = move(orderId, "46.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("WORKING"))
                .andExpect(jsonPath("$.triggerPrice").value(46.5))
                .andReturn().getResponse().getContentAsString();
        Integer newId = JsonPath.read(body, "$.orderId");

        Orders old = order(orderId);
        assertEquals("CANCELED", old.getOrderStatus());
        assertTrue(old.getRejectionReason().startsWith("Replaced by order #" + newId), old.getRejectionReason());
        assertAmount("48", old.getTriggerPrice());
        Orders moved = order(newId);
        assertEquals("LIMIT", moved.getOrderKind());
        assertAmount("10", moved.getQuantity());

        mockMvc.perform(get("/orders/working").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(newId));
    }

    @Test
    void aMoveThatBreaksARuleLeavesTheOrderWorkingAsItWas() throws Exception {
        Integer orderId = placeWorking("BUY", "10", "LIMIT", "48");
        long ordersBefore = orderRepository.count();

        move(orderId, "52")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRICE_ALREADY_REACHED"))
                .andExpect(jsonPath("$.orderId").doesNotExist());

        assertEquals("WORKING", order(orderId).getOrderStatus());
        assertNull(order(orderId).getRejectionReason());
        assertEquals(ordersBefore, orderRepository.count());
    }

    @Test
    void whenAStopLossOrTakeProfitSellsTheSharesTheOtherIsCanceled() throws Exception {
        hold("10");
        Integer stopLoss = placeWorking("SELL", "10", "STOP", "45");
        Integer takeProfit = placeWorking("SELL", "10", "LIMIT", "55");

        priceMovesTo("55.10");
        assertEquals(1, workingOrderService.fillOrdersAtTriggerPrice());

        assertEquals("FILLED", order(takeProfit).getOrderStatus());
        Orders other = order(stopLoss);
        assertEquals("CANCELED", other.getOrderStatus());
        assertTrue(other.getRejectionReason().startsWith("Canceled automatically: order #" + takeProfit), other.getRejectionReason());
        assertAmount("0", held());
    }

    @Test
    void anotherTradersOrderCantBeCanceledOrMoved() throws Exception {
        Integer orderId = placeWorking("BUY", "10", "LIMIT", "48");
        bearer = newTrader(UUID.randomUUID().toString().substring(0, 8)).bearer();

        cancel(orderId).andExpect(status().isForbidden());
        move(orderId, "47").andExpect(status().isForbidden());
        assertEquals("WORKING", order(orderId).getOrderStatus());
    }
}
