package com.neueda.leap.service;

import com.neueda.leap.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Finishes orders the platform lost track of (BR-06, BR-14, section 9.1). An accepted order waits for its
 * fill on an in-memory timer, so a restart or crash during that half second would otherwise leave it
 * ACCEPTED forever. This sweep runs at startup and then every few seconds (OrderRecoveryScheduler):
 * <ul>
 *   <li>ACCEPTED orders well past their fill delay are filled now if the accepted price is still the
 *       current one, or marked FAILED with the reason. The intent was recorded, so the outcome is too.</li>
 *   <li>SUBMITTED orders past the grace period never finished their trading checks (the checks run in one
 *       transaction, so this only happens to records from a crash mid-write), so they are REJECTED.</li>
 * </ul>
 * Every step locks the order row, so the sweep and a fill timer that is still running never both act on
 * one order, and several backend instances can sweep at once.
 */
@Service
public class OrderRecoveryService {
    private static final Logger logger = Logger.getLogger(OrderRecoveryService.class.getName());

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final OrderExecutionService orderExecutionService;
    private final Duration orderDelay;
    private final Duration grace;
    private final Clock clock;

    @Autowired
    public OrderRecoveryService(OrderRepository orderRepository, OrderService orderService,
                                OrderExecutionService orderExecutionService,
                                @Value("${leap.trading.order-delay:PT0.5S}") Duration orderDelay,
                                @Value("${leap.trading.recovery-grace:PT1.5S}") Duration grace) {
        this(orderRepository, orderService, orderExecutionService, orderDelay, grace, Clock.systemDefaultZone());
    }

    public OrderRecoveryService(OrderRepository orderRepository, OrderService orderService,
                                OrderExecutionService orderExecutionService, Duration orderDelay, Duration grace,
                                Clock clock) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.orderExecutionService = orderExecutionService;
        this.orderDelay = orderDelay;
        this.grace = grace;
        this.clock = clock;
    }

    /** Closes every unfinished order old enough to have been forgotten; returns how many it looked at. */
    public int recover() {
        LocalDateTime now = LocalDateTime.now(clock);

        List<Integer> unchecked = orderRepository.findIdsByStatusSubmittedBefore("SUBMITTED", now.minus(grace));
        for (Integer orderId : unchecked) {
            if (orderExecutionService.rejectUnchecked(orderId)) {
                logger.warning("Order " + orderId + " was left SUBMITTED; rejected it, nothing moved");
            }
        }

        List<Integer> unfilled = orderRepository.findIdsByStatusSubmittedBefore("ACCEPTED", now.minus(orderDelay).minus(grace));
        for (Integer orderId : unfilled) {
            logger.warning("Order " + orderId + " was left ACCEPTED, its fill timer lost; filling or failing it now");
            orderService.fillOrFail(orderId, true);
        }
        return unchecked.size() + unfilled.size();
    }
}
