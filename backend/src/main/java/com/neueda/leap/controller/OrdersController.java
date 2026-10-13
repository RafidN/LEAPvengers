package com.neueda.leap.controller;

import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.model.dto.OrderHistoryResult;
import com.neueda.leap.service.OrderQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The signed-in trader's orders and order timelines.
 * Errors (400 bad period/order, 403 someone else's order) are mapped by GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/orders")
public class OrdersController {

    private final OrderQueryService orderQueryService;

    public OrdersController(OrderQueryService orderQueryService) {
        this.orderQueryService = orderQueryService;
    }

    /** ?period=1d|7d|1m|1y (optional, default all) and ?order=desc|asc (optional, default newest first). */
    @GetMapping
    public ResponseEntity<List<OrderHistoryResult>> getOrders(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "order", required = false) String order) {
        return ResponseEntity.ok(orderQueryService.getOrdersForCurrentUser(period, order));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<OrderEventResult>> getOrderEvents(@PathVariable("id") Integer orderId) {
        return ResponseEntity.ok(orderQueryService.getOrderEvents(orderId));
    }
}
