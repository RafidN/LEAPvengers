package com.neueda.leap.controller;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.service.OrderQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrdersControllerTest {

    private MockMvc mockMvc;
    private StubOrderQueryService stubOrderQueryService;

    @BeforeEach
    void setUp() {
        stubOrderQueryService = new StubOrderQueryService();
        OrdersController controller = new OrdersController(stubOrderQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void testGetOrders() throws Exception {
        mockMvc.perform(get("/orders").param("period", "7d").param("order", "asc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].orderId").value(12))
            .andExpect(jsonPath("$[0].ticker").value("ASML"))
            .andExpect(jsonPath("$[0].orderStatus").value("REJECTED"))
            .andExpect(jsonPath("$[0].rejectionReason").value("Insufficient buying power for the order"))
            .andExpect(jsonPath("$[0].accountId").value(3));

        assertEquals("7d", stubOrderQueryService.lastPeriod);
        assertEquals("asc", stubOrderQueryService.lastOrder);
    }

    @Test
    void testGetOrderEvents() throws Exception {
        mockMvc.perform(get("/orders/12/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].toStatus").value("ACCEPTED"))
            .andExpect(jsonPath("$[1].fromStatus").value("ACCEPTED"))
            .andExpect(jsonPath("$[1].toStatus").value("FILLED"));
    }

    @Test
    void testGetOrderEventsForbiddenForOtherTradersOrder() throws Exception {
        mockMvc.perform(get("/orders/99/events"))
            .andExpect(status().isForbidden());
    }

    private static class StubOrderQueryService extends OrderQueryService {
        String lastPeriod;
        String lastOrder;

        StubOrderQueryService() {
            super(null, null, null, null);
        }

        @Override
        public List<OrderHistoryResult> getOrdersForCurrentUser(String period, String order) {
            lastPeriod = period;
            lastOrder = order;
            return List.of(new OrderHistoryResult(12, "ASML", "BUY", new BigDecimal("20"), new BigDecimal("1850.00"),
                "REJECTED", LocalDate.of(2026, 10, 6), LocalDateTime.of(2026, 10, 6, 13, 0),
                3, "Insufficient buying power for the order", null, null));
        }

        @Override
        public List<OrderEventResult> getOrderEvents(Integer orderId) {
            if (orderId == 99) {
                throw new ForbiddenException("User does not own the specified order");
            }
            return List.of(
                new OrderEventResult(1, orderId, 7, "joanna", null, "ACCEPTED", LocalDateTime.of(2026, 10, 1, 9, 0), null),
                new OrderEventResult(2, orderId, null, null, "ACCEPTED", "FILLED", LocalDateTime.of(2026, 10, 1, 9, 0, 2), "Filled at 76.5000")
            );
        }
    }
}
