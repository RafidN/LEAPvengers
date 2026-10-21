package com.neueda.leap.service;

import com.neueda.leap.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Watches WORKING limit and stop orders against the latest prices (WorkingOrderScheduler runs it every second;
 * the price feed writes every few). An order whose price has been reached is triggered (WORKING -> ACCEPTED) and
 * filled straight away through the same fill as every other order, or marked FAILED with the reason. One that
 * can't be traded on yet, e.g. because its market is closed, keeps working.
 * <p>
 * Nothing is held in memory: the orders and prices are read from the database each time, so working orders
 * survive restarts, and the row locks in OrderExecutionService keep several backend instances from triggering
 * one order twice.
 */
@Service
public class WorkingOrderService {
    private static final Logger logger = Logger.getLogger(WorkingOrderService.class.getName());

    private final OrderRepository orderRepository;
    private final OrderExecutionService orderExecutionService;
    private final OrderService orderService;

    public WorkingOrderService(OrderRepository orderRepository, OrderExecutionService orderExecutionService,
                               OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderExecutionService = orderExecutionService;
        this.orderService = orderService;
    }

    /** Triggers and fills every working order whose price has been reached; returns how many were triggered. */
    public int fillOrdersAtTriggerPrice() {
        List<Integer> reached = orderRepository.findWorkingOrderIdsAtTriggerPrice();
        int triggered = 0;
        for (Integer orderId : reached) {
            try {
                if (orderExecutionService.trigger(orderId)) {
                    triggered++;
                    orderService.fillOrFail(orderId, false);
                }
            } catch (Exception e) {
                // Still WORKING (the trigger rolled back) or ACCEPTED (the recovery sweep fills it); next run retries
                logger.log(Level.WARNING, "Could not trigger working order " + orderId, e);
            }
        }
        return triggered;
    }
}
