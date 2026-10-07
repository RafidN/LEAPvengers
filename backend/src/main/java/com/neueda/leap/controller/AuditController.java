package com.neueda.leap.controller;

import com.neueda.leap.model.dto.AuditOrdersRequest;
import com.neueda.leap.model.dto.AuditResponse;
import com.neueda.leap.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
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
     * Retrieve all orders across all clients with optional filtering
     * 
     * Query parameters:
     * - clientId: Filter by client (optional)
     * - ticker: Filter by instrument ticker (optional)
     * - orderStatus: Filter by order status (optional)
     * - startDate: Filter by order date start (optional)
     * - endDate: Filter by order date end (optional)
     * - pageNumber: Page number, 1-indexed (default: 1)
     * - pageSize: Number of records per page, max 100 (default: 20)
     * 
     * @return Paginated orders with client and instrument details
     */
    @GetMapping("/orders")
    public AuditResponse getAuditOrders(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(required = false) String ticker,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {

        // Build request object
        AuditOrdersRequest request = new AuditOrdersRequest();
        request.setClientId(clientId);
        request.setTicker(ticker);
        request.setOrderStatus(orderStatus);
        request.setPageNumber(pageNumber);
        request.setPageSize(pageSize);

        // Parse date strings to LocalDate
        if (startDate != null && !startDate.isEmpty()) {
            request.setStartDate(java.time.LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            request.setEndDate(java.time.LocalDate.parse(endDate));
        }

        // Get audit data
        return new AuditResponse(auditService.getAuditOrders(request));
    }
}
