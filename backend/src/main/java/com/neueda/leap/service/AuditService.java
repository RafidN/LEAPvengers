package com.neueda.leap.service;

import com.neueda.leap.model.dto.AuditData;
import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.model.dto.AuditOrdersRequest;
import com.neueda.leap.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Service for audit operations
 * Handles retrieval of cross-client order data with filtering and paging
 */
@Service
public class AuditService {

    @Autowired
    private AuditRepository auditRepository;

    /**
     * Get all orders with optional filtering and pagination
     * 
     * @param request Filter criteria and pagination info
     * @return AuditData containing filtered orders and pagination metadata
     */
    public AuditData getAuditOrders(AuditOrdersRequest request) {
        // Validate page number (1-based)
        int pageNumber = Math.max(1, request.getPageNumber());
        int pageSize = Math.max(1, Math.min(request.getPageSize(), 100)); // Cap at 100

        // Convert to 0-based for Spring Data
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);

        // Execute query with filters
        Page<AuditOrder> page = auditRepository.findAuditOrders(
            request.getClientId(),
            request.getTicker(),
            request.getOrderStatus(),
            request.getStartDate(),
            request.getEndDate(),
            pageable
        );

        // Build response with pagination metadata
        return new AuditData(
            page.getContent(),
            pageNumber,
            pageSize,
            page.getTotalElements()
        );
    }
}
