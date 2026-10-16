package com.neueda.leap.controller;

import com.neueda.leap.model.dto.AuditClientDetailsResponse;
import com.neueda.leap.model.dto.AuditClientSummary;
import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.model.dto.AuditOrderDetailsResponse;
import com.neueda.leap.model.dto.DisputeRequest;
import com.neueda.leap.model.dto.DisputeResult;
import com.neueda.leap.service.AuditService;
import com.neueda.leap.service.DisputeService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for audit endpoints
 * Requires OPS role
 */
@RestController
@RequestMapping("/internal/audit")
public class AuditController {
    private final AuditService auditService;
    private final DisputeService disputeService;

    public AuditController(AuditService auditService, DisputeService disputeService) {
        this.auditService = auditService;
        this.disputeService = disputeService;
    }

    /** GET /internal/audit/disputes?status=OPEN|RESOLVED: clients' disputes, newest first (all when no status). */
    @GetMapping("/disputes")
    public List<DisputeResult> getDisputes(@RequestParam(required = false) String status) {
        return disputeService.all(status);
    }

    /** POST /internal/audit/disputes/{id}/resolve {"resolution": "..."}: closes it with an answer the client sees. */
    @PostMapping("/disputes/{id}/resolve")
    public DisputeResult resolveDispute(@PathVariable("id") Integer disputeId, @RequestBody DisputeRequest request) {
        return disputeService.resolve(disputeId, request);
    }

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

    @GetMapping("/orders/{id}")
    public AuditOrderDetailsResponse getAuditById(@PathVariable("id") Integer orderId) {
        return auditService.getAuditById(orderId);
    }

    @GetMapping("/clients")
    public List<AuditClientSummary> searchClients(
            @RequestParam(required = false) String query) {
        return auditService.searchClients(query);
    }

    @GetMapping("/clients/{id}")
    public AuditClientDetailsResponse getClientDetails(@PathVariable("id") Integer clientId) {
        return auditService.getClientDetails(clientId);
    }
}
