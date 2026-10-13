package com.neueda.leap.service;

import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.repository.AuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Service for audit order queries
 * Handles pagination logic and parameter validation
 */
@Service
public class AuditService {

    @Autowired
    private AuditRepository auditRepository;

    /**
     * Get all orders with filtering and pagination
     */
    public Page<AuditOrder> getAuditOrders(Integer clientId, String ticker, String orderStatus,
                                           String rejectionReason, String startDate, String endDate, int page, int size) {
        // Validate and cap page size
        size = Math.max(1, Math.min(size, 100));

        // Parse dates
        LocalDate start = startDate != null && !startDate.isEmpty() ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null && !endDate.isEmpty() ? LocalDate.parse(endDate) : null;

        // Execute query
        Pageable pageable = PageRequest.of(page, size);
        return auditRepository.findAuditOrders(clientId, ticker, orderStatus, rejectionReason, start, end, pageable);
    }
}
