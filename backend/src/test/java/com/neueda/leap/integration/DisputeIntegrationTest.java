package com.neueda.leap.integration;

import com.jayway.jsonpath.JsonPath;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Clients;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.InstrumentsRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.UserRepository;
import com.neueda.leap.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A client disputes one of their orders from History, Operations sees it with the order's record and
 * resolves it, and the client sees the answer. Only the order's owner can dispute it, and only Ops can
 * see everyone's disputes or resolve them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisputeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private InstrumentsRepository instrumentsRepository;
    @Autowired private OrderRepository orderRepository;

    private String trader;
    private String otherTrader;
    private String ops;
    private String analyst;
    private Integer failedOrderId;
    private Integer acceptedOrderId;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        trader = bearer(newTrader("d1-" + unique));
        otherTrader = bearer(newTrader("d2-" + unique));
        ops = bearer(staff("ops-" + unique, "OPS"));
        analyst = bearer(staff("an-" + unique, "ANALYST"));

        Users owner = userRepository.findByUsername("d1-" + unique).orElseThrow();
        Integer accountId = accountRepository.findAccountResponsesByClientId(owner.getClientId()).get(0).getAccountId();
        Integer instrumentId = instrumentsRepository.save(new Instruments("D" + unique.toUpperCase(), "Dispute instrument", "Equity", "US"))
                .getInstrumentId();
        failedOrderId = order(accountId, instrumentId, "FAILED");
        acceptedOrderId = order(accountId, instrumentId, "ACCEPTED");
    }

    private Users newTrader(String username) {
        Clients client = clientRepository.save(new Clients("Dee", "Spute", username + "@example.com"));
        accountRepository.save(new Accounts(client.getClientId(), LocalDate.now(), new BigDecimal("1000")));
        return userRepository.save(new Users(client.getClientId(), username, "not-used"));
    }

    private Users staff(String username, String role) {
        Users user = new Users(null, username, "not-used");
        user.setRole(role);
        return userRepository.save(user);
    }

    private String bearer(Users user) {
        return "Bearer " + jwtUtil.generateToken(user);
    }

    private Integer order(Integer accountId, Integer instrumentId, String status) {
        Orders order = new Orders(accountId, instrumentId, "BUY", new BigDecimal("5"), new BigDecimal("10"), LocalDate.now());
        order.setOrderStatus(status);
        if ("FAILED".equals(status)) {
            order.setRejectionReason("The price moved");
        }
        return orderRepository.save(order).getOrderId();
    }

    private ResultActions raise(String who, Integer orderId, String reason, String message) throws Exception {
        return mockMvc.perform(post("/orders/" + orderId + "/disputes").header("Authorization", who)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\",\"message\":\"" + message + "\"}"));
    }

    @Test
    void clientRaisesOpsResolvesClientSeesTheAnswer() throws Exception {
        String body = raise(trader, failedOrderId, "SHOULD_HAVE_FILLED", "I had the cash, why did it fail?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.orderId").value(failedOrderId))
                // The client's own view leaves out client details
                .andExpect(jsonPath("$.clientName").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Integer disputeId = JsonPath.read(body, "$.disputeId");

        // Ops sees it in the open list, with who raised it, and on the order's record
        mockMvc.perform(get("/internal/audit/disputes?status=OPEN").header("Authorization", ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.disputeId == " + disputeId + ")].clientName").value("Dee Spute"))
                .andExpect(jsonPath("$[?(@.disputeId == " + disputeId + ")].message").value("I had the cash, why did it fail?"));

        mockMvc.perform(post("/internal/audit/disputes/" + disputeId + "/resolve").header("Authorization", ops)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"The price moved before the fill; nothing was charged.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());

        // Resolved once only
        mockMvc.perform(post("/internal/audit/disputes/" + disputeId + "/resolve").header("Authorization", ops)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"Again\"}"))
                .andExpect(status().isBadRequest());

        // The client sees the answer, from "Operations" rather than a staff username
        mockMvc.perform(get("/orders/disputes").header("Authorization", trader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$[0].resolution").value("The price moved before the fill; nothing was charged."))
                .andExpect(jsonPath("$[0].resolvedByUsername").value("Operations"));

        // Once resolved, the client can raise another
        raise(trader, failedOrderId, "OTHER", "Still not happy").andExpect(status().isCreated());
    }

    @Test
    void oneOpenDisputePerOrder() throws Exception {
        raise(trader, failedOrderId, "WRONG_PRICE", "First").andExpect(status().isCreated());
        raise(trader, failedOrderId, "WRONG_PRICE", "Second")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You already have an open dispute on this order"));
    }

    @Test
    void requestsThatMakeNoSenseAreRefused() throws Exception {
        raise(trader, failedOrderId, "BORED", "Hello").andExpect(status().isBadRequest());
        raise(trader, failedOrderId, "OTHER", " ").andExpect(status().isBadRequest());
        // Still going through: wait for the outcome first
        raise(trader, acceptedOrderId, "OTHER", "Where is it?").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerCanDisputeAndOnlyOpsCanWorkDisputes() throws Exception {
        raise(otherTrader, failedOrderId, "OTHER", "Not mine").andExpect(status().isForbidden());
        raise(ops, failedOrderId, "OTHER", "Staff can't").andExpect(status().isForbidden());

        mockMvc.perform(get("/internal/audit/disputes").header("Authorization", trader)).andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/audit/disputes").header("Authorization", analyst)).andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/audit/disputes/1/resolve").header("Authorization", trader)
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\":\"x\"}")).andExpect(status().isForbidden());

        // Another trader's dispute list never includes this one
        raise(trader, failedOrderId, "OTHER", "Mine").andExpect(status().isCreated());
        mockMvc.perform(get("/orders/disputes").header("Authorization", otherTrader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
