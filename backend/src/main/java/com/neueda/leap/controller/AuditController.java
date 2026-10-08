package com.neueda.leap.controller;

import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for audit endpoints
 * Requires OPS role
 */
@RestController
@RequestMapping("/internal/audit")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /**
     * GET /internal/audit/orders
     * Retrieve all orders across all clients with optional filtering, newest first, with paging
     */
    @GetMapping("/orders")
    public Page<AuditOrder> getAuditOrders(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String rejection_reason,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditService.getAuditOrders(clientId, ticker, orderStatus, rejection_reason, startDate, endDate, page, size);
    }
}
