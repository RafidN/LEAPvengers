package com.neueda.leap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs: the reporting store load (ReportingLoadScheduler), the unfinished-order
 * sweep (OrderRecoveryScheduler) and the working limit and stop order check (WorkingOrderScheduler). Each job
 * has its own switch, so tests can turn them off one at a time.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
