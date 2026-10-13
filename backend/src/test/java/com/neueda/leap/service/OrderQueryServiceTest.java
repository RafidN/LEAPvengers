package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.repository.OrderEventsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueryServiceTest {

    private static final int USER_ID = 7;
    private static final int CLIENT_ID = 70;

    @Mock
    private OrderEventsRepository orderEventsRepository;

    private StubHistoryService historyService;

    private OrderQueryService orderQueryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        historyService = new StubHistoryService();
        orderQueryService = new OrderQueryService(
            historyService, orderEventsRepository, new StubOwnershipService(Set.of(42)), new StubCurrentUserService());
    }

    @Test
    void testGetOrdersNewestFirstByDefault() {
        historyService.orders = List.of(order(2), order(1));

        List<OrderHistoryResult> orders = orderQueryService.getOrdersForCurrentUser("1m", null);

        assertEquals(List.of(2, 1), orders.stream().map(OrderHistoryResult::getOrderId).toList());
        assertEquals(USER_ID, historyService.lastUserId);
        assertEquals("1m", historyService.lastPeriod);
    }

    @Test
    void testGetOrdersAscendingReversesOrder() {
        historyService.orders = List.of(order(3), order(2), order(1));

        List<OrderHistoryResult> orders = orderQueryService.getOrdersForCurrentUser(null, "ASC");

        assertEquals(List.of(1, 2, 3), orders.stream().map(OrderHistoryResult::getOrderId).toList());
    }

    @Test
    void testGetOrdersRejectsUnknownSortOrder() {
        assertThrows(InvalidInputException.class,
            () -> orderQueryService.getOrdersForCurrentUser(null, "sideways"));
    }

    @Test
    void testGetOrderEventsReturnsOwnedOrderTimeline() {
        List<OrderEventResult> events = List.of(
            new OrderEventResult(1, 42, USER_ID, "joanna", null, "ACCEPTED", LocalDateTime.of(2026, 10, 1, 9, 0), null),
            new OrderEventResult(2, 42, null, null, "ACCEPTED", "FILLED", LocalDateTime.of(2026, 10, 1, 9, 0, 2), "Filled at $76.50")
        );
        when(orderEventsRepository.findOrderEventsByOrderId(CLIENT_ID, 42)).thenReturn(events);

        assertEquals(events, orderQueryService.getOrderEvents(42));
    }

    @Test
    void testGetOrderEventsForbiddenForOtherClientsOrder() {
        assertThrows(ForbiddenException.class, () -> orderQueryService.getOrderEvents(99));
        verify(orderEventsRepository, never()).findOrderEventsByOrderId(any(), any());
    }

    private static OrderHistoryResult order(int orderId) {
        return new OrderHistoryResult(orderId, "NFLX", "BUY", BigDecimal.ONE, BigDecimal.TEN, "FILLED",
            LocalDate.of(2026, 10, 1), LocalDateTime.of(2026, 10, 1, 9, orderId));
    }

    private static class StubHistoryService extends HistoryService {
        List<OrderHistoryResult> orders = List.of();
        Integer lastUserId;
        String lastPeriod;

        StubHistoryService() {
            super(null, null, null, null, null);
        }

        @Override
        public List<OrderHistoryResult> getOrderHistory(Integer userId, String period) {
            lastUserId = userId;
            lastPeriod = period;
            return orders;
        }
    }

    private static class StubOwnershipService extends OwnershipService {
        private final Set<Integer> ownedOrderIds;

        StubOwnershipService(Set<Integer> ownedOrderIds) {
            super(null, null, null);
            this.ownedOrderIds = ownedOrderIds;
        }

        @Override
        public void requireOrder(Integer orderId) {
            if (!ownedOrderIds.contains(orderId)) {
                throw new ForbiddenException("User does not own the specified order");
            }
        }
    }

    private static class StubCurrentUserService extends CurrentUserService {
        StubCurrentUserService() {
            super(null);
        }

        @Override
        public Users getCurrentUser() {
            Users user = new Users();
            user.setUserId(USER_ID);
            user.setClientId(CLIENT_ID);
            return user;
        }
    }
}
