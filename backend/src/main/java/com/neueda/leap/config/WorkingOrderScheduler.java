package com.neueda.leap.config;

import com.neueda.leap.service.WorkingOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks working limit and stop orders against the latest prices every leap.trading.working-orders-interval
 * (a second by default). Switched off with leap.trading.working-orders-enabled=false, e.g. in tests, which call
 * WorkingOrderService themselves.
 */
@Configuration
@ConditionalOnProperty(name = "leap.trading.working-orders-enabled", havingValue = "true", matchIfMissing = true)
public class WorkingOrderScheduler {
    private static final Logger logger = Logger.getLogger(WorkingOrderScheduler.class.getName());

    private final WorkingOrderService workingOrderService;

    public WorkingOrderScheduler(WorkingOrderService workingOrderService) {
        this.workingOrderService = workingOrderService;
    }

    @Scheduled(initialDelayString = "${leap.trading.working-orders-initial-delay:PT5S}",
               fixedDelayString = "${leap.trading.working-orders-interval:PT1S}")
    public void fillWorkingOrders() {
        try {
            workingOrderService.fillOrdersAtTriggerPrice();
        } catch (Exception e) {
            // Tried again on the next run; the orders keep working meanwhile
            logger.log(Level.WARNING, "Working order check failed", e);
        }
    }
}
