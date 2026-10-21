package com.neueda.leap.controller;

import com.neueda.leap.model.dto.ActivityReportResponse;
import com.neueda.leap.model.dto.OrderHealthReportResponse;
import com.neueda.leap.service.ReportingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Internal reporting on trading activity, for Analysts and Ops (see SecurityConfig: /internal/reports/**).
 * Served from the reporting store, not the live trading tables. Bad input answers 400 via GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/internal/reports")
public class ReportsController {

    private final ReportingService reportingService;

    public ReportsController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    /**
     * ?from=2026-09-01&to=2026-09-30 (inclusive, default the last 30 days)
     * &groupBy=day|week|month|instrument|assetClass|segment (default day)
     * &segment=Premier (optional: one client segment only)
     */
    @GetMapping("/activity")
    public ResponseEntity<ActivityReportResponse> getActivity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String groupBy,
            @RequestParam(required = false) String segment) {
        return ResponseEntity.ok(reportingService.getActivity(from, to, groupBy, segment));
    }

    /**
     * ?from=2026-09-01&to=2026-09-30 (inclusive, default the last 90 days)
     */
    @GetMapping("/order-health")
    public ResponseEntity<OrderHealthReportResponse> getOrderHealth(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportingService.getOrderHealth(from, to));
    }
}
