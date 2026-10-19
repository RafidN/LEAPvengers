package com.neueda.leap.config;

import com.neueda.leap.service.ReportingLoadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the reporting store load on a timer (leap.reporting.load-interval, every minute by default).
 * Switched off with leap.reporting.load-enabled=false, e.g. in tests, which call the load themselves.
 */
@Configuration
@ConditionalOnProperty(name = "leap.reporting.load-enabled", havingValue = "true", matchIfMissing = true)
public class ReportingLoadScheduler {
    private static final Logger logger = Logger.getLogger(ReportingLoadScheduler.class.getName());

    private final ReportingLoadService reportingLoadService;

    public ReportingLoadScheduler(ReportingLoadService reportingLoadService) {
        this.reportingLoadService = reportingLoadService;
    }

    @Scheduled(initialDelayString = "${leap.reporting.initial-delay:PT1.5S}",
               fixedDelayString = "${leap.reporting.load-interval:PT1M}")
    public void loadReportingStore() {
        try {
            int loaded = reportingLoadService.load();
            if (loaded > 0) {
                logger.info("Reporting store: loaded " + loaded + " new fills");
            }
        } catch (Exception e) {
            // The next run picks up where this one failed; live trading is unaffected either way
            logger.log(Level.WARNING, "Reporting store load failed", e);
        }
    }
}
