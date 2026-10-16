package com.neueda.leap.integration;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.OrderEvents;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.CashTransactionResult;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.CashTransactionRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.service.OrderRecoveryService;
import com.neueda.leap.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-06 / BR-14 / section 9.1: an order the platform lost track of (a restart during the half-second fill
 * delay) is finished by the recovery sweep, against the real database, and a fill can never happen twice.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderRecoveryIntegrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentsRepository instrumentsRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderEventsRepository orderEventsRepository;
    @Autowired private OrderRecoveryService orderRecoveryService;
    @Autowired private OrderService orderService;
    @Autowired private CashTransactionRepository cashTransactionRepository;

    private Integer accountId;
    private Integer instrumentId;

    /** A trader with 10,000 cash and an instrument quoted at 50 right now. */
    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Clients client = clientRepository.save(new Clients("Recovery", "Trader", unique + "@example.com"));
        userRepository.save(new Users(client.getClientId(), "recovery-" + unique, "not-used"));
        accountId = accountRepository.save(new Accounts(client.getClientId(), LocalDate.now(), new BigDecimal("10000"))).getAccountId();
        instrumentId = instrumentsRepository.save(new Instruments("V" + unique.toUpperCase(), "Recovery instrument", "Equity", "US"))
                .getInstrumentId();
        jdbc.update("INSERT INTO latest_price_quotes (instrument_id, price, quote_timestamp, source) VALUES (?, ?, ?, 'YAHOO_LIVE')",
                instrumentId, new BigDecimal("50"), LocalDateTime.now());
    }

    /** An order as a crash would leave it: in this status, submitted a while ago, its timer gone. */
    private Integer orderLeft(String status, String price, LocalDateTime submittedAt) {
        Orders order = new Orders(accountId, instrumentId, "BUY", new BigDecimal("10"), new BigDecimal(price), LocalDate.now());
        order.setOrderStatus(status);
        order.setSubmittedAt(submittedAt);
        return orderRepository.save(order).getOrderId();
    }

    private Orders reload(Integer orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private BigDecimal cash() {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private List<OrderEvents> events(Integer orderId) {
        return orderEventsRepository.findAll().stream().filter(e -> e.getOrderId().equals(orderId)).toList();
    }

    @Test
    void acceptedOrderWhosePriceStillHoldsIsFilledOnRecovery() {
        Integer orderId = orderLeft("ACCEPTED", "50", LocalDateTime.now().minusMinutes(1));

        orderRecoveryService.recover();

        Orders order = reload(orderId);
        assertEquals("FILLED", order.getOrderStatus());
        // 10 x 50 = 500
        assertEquals(0, new BigDecimal("9500").compareTo(cash()));
        OrderEvents fill = events(orderId).get(0);
        assertEquals("FILLED", fill.getToStatus());
        assertEquals("YAHOO_LIVE", fill.getQuoteSource());

        // What it settled as, as the Ops audit trail shows it: the cash the fill moved, with its time
        CashTransactionResult settlement = cashTransactionRepository.findSettlementByOrderId(orderId).orElseThrow();
        assertEquals("BUY", settlement.getTransactionType());
        assertEquals(0, new BigDecimal("500").compareTo(settlement.getAmount()));
        assertTrue(settlement.getTransactionTime() != null);
    }

    @Test
    void acceptedOrderWhosePriceMovedFailsSayingThePlatformRestarted() {
        Integer orderId = orderLeft("ACCEPTED", "48", LocalDateTime.now().minusMinutes(1));

        orderRecoveryService.recover();

        Orders order = reload(orderId);
        assertEquals("FAILED", order.getOrderStatus());
        assertTrue(order.getRejectionReason().startsWith("The platform restarted before this order was filled. The price moved"),
                order.getRejectionReason());
        assertEquals(0, new BigDecimal("10000").compareTo(cash()));
        OrderEvents failure = events(orderId).get(0);
        assertEquals(0, new BigDecimal("50").compareTo(failure.getQuotePrice()));
    }

    @Test
    void orderLeftSubmittedIsRejectedOnRecovery() {
        Integer orderId = orderLeft("SUBMITTED", "50", LocalDateTime.now().minusMinutes(1));

        orderRecoveryService.recover();

        assertEquals("REJECTED", reload(orderId).getOrderStatus());
        assertEquals(0, new BigDecimal("10000").compareTo(cash()));
    }

    @Test
    void orderStillWithinItsDelayIsLeftForItsTimer() {
        Integer orderId = orderLeft("ACCEPTED", "50", LocalDateTime.now());

        orderRecoveryService.recover();

        assertEquals("ACCEPTED", reload(orderId).getOrderStatus());
    }

    @Test
    void recoveringTwiceFillsOnce() {
        Integer orderId = orderLeft("ACCEPTED", "50", LocalDateTime.now().minusMinutes(1));

        orderRecoveryService.recover();
        orderRecoveryService.recover();

        assertEquals("FILLED", reload(orderId).getOrderStatus());
        assertEquals(0, new BigDecimal("9500").compareTo(cash()));
        assertEquals(1, events(orderId).size());
    }

    @Test
    void timerAndSweepRacingForOneOrderFillItOnce() throws Exception {
        Integer orderId = orderLeft("ACCEPTED", "50", LocalDateTime.now().minusMinutes(1));

        // Both try to fill at the same moment; the order row lock makes the second wait, then find it FILLED
        ExecutorService threads = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 4; i++) {
            boolean asSweep = i % 2 == 0;
            threads.submit(() -> {
                start.await();
                orderService.fillOrFail(orderId, asSweep);
                return null;
            });
        }
        start.countDown();
        threads.shutdown();
        assertTrue(threads.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals("FILLED", reload(orderId).getOrderStatus());
        assertEquals(0, new BigDecimal("9500").compareTo(cash()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM cash_transactions WHERE order_id = ?", Integer.class, orderId));
        assertEquals(1, events(orderId).size());
    }
}
