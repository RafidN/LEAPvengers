package com.neueda.leap.config;

import com.neueda.leap.service.OrderRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the unfinished-order sweep shortly after startup, then every leap.trading.recovery-interval
 * (10 seconds by default). Switched off with leap.trading.recovery-enabled=false, e.g. in tests, which
 * call OrderRecoveryService themselves.
 */
@Configuration
@ConditionalOnProperty(name = "leap.trading.recovery-enabled", havingValue = "true", matchIfMissing = true)
public class OrderRecoveryScheduler {
    private static final Logger logger = Logger.getLogger(OrderRecoveryScheduler.class.getName());

    private final OrderRecoveryService orderRecoveryService;

    public OrderRecoveryScheduler(OrderRecoveryService orderRecoveryService) {
        this.orderRecoveryService = orderRecoveryService;
    }

    @Scheduled(initialDelayString = "${leap.trading.recovery-initial-delay:PT2S}",
               fixedDelayString = "${leap.trading.recovery-interval:PT10S}")
    public void recoverUnfinishedOrders() {
        try {
            orderRecoveryService.recover();
        } catch (Exception e) {
            // Tried again on the next run; the orders stay recorded as they are meanwhile
            logger.log(Level.WARNING, "Unfinished order sweep failed", e);
        }
    }
}
