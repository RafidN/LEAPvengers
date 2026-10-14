package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.repository.OrderEventsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only order queries for the signed-in trader: their orders and each order's timeline.
 */
@Service
public class OrderQueryService {
    private static final String ORDER_ASC = "asc";
    private static final String ORDER_DESC = "desc";

    private final HistoryService historyService;
    private final OrderEventsRepository orderEventsRepository;
    private final OwnershipService ownershipService;
    private final CurrentUserService currentUserService;

    public OrderQueryService(HistoryService historyService,
                             OrderEventsRepository orderEventsRepository,
                             OwnershipService ownershipService,
                             CurrentUserService currentUserService) {
        this.historyService = historyService;
        this.orderEventsRepository = orderEventsRepository;
        this.ownershipService = ownershipService;
        this.currentUserService = currentUserService;
    }

    /**
     * Orders across all of the trader's accounts, newest first unless order=asc.
     * period (1d|7d|1m|1y) is optional; omitting it returns every order.
     */
    @Transactional(readOnly = true)
    public List<OrderHistoryResult> getOrdersForCurrentUser(String period, String order) {
        boolean ascending = isAscending(order);
        Users user = currentUserService.getCurrentUser();
        List<OrderHistoryResult> orders = historyService.getOrderHistory(user.getUserId(), period);
        if (ascending) {
            orders = new ArrayList<>(orders);
            Collections.reverse(orders);
        }
        return orders;
    }

    /**
     * One order's events, oldest first. Throws ForbiddenException unless the order belongs to the trader.
     */
    @Transactional(readOnly = true)
    public List<OrderEventResult> getOrderEvents(Integer orderId) {
        ownershipService.requireOrder(orderId);
        Users user = currentUserService.getCurrentUser();
        return orderEventsRepository.findOrderEventsByOrderId(user.getClientId(), orderId);
    }

    private boolean isAscending(String order) {
        if (order == null || order.isBlank()) {
            return false;
        }
        return switch (order.trim().toLowerCase()) {
            case ORDER_ASC -> true;
            case ORDER_DESC -> false;
            default -> throw new InvalidInputException("Unsupported order: " + order + " (use asc or desc)");
        };
    }
}
