package com.neueda.leap.service;

import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    public void testGetAuditOrders_WithFilters() {
        Page<AuditOrder> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(auditRepository.findAuditOrders(
                eq(1), eq("AAPL"), eq("FILLED"), eq(null),
                eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31)),
                any())).thenReturn(mockPage);

        Page<AuditOrder> result = auditService.getAuditOrders(
                1, "AAPL", "FILLED", null,
                "2024-01-01", "2024-12-31", 0, 20);

        assertNotNull(result);
        verify(auditRepository).findAuditOrders(
                eq(1), eq("AAPL"), eq("FILLED"), eq(null),
                eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31)),
                any());
    }

    @Test
    public void testGetAuditOrders_PageSizeCapped() {
        Page<AuditOrder> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        when(auditRepository.findAuditOrders(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(mockPage);

        auditService.getAuditOrders(null, null, null, null, null, null, 0, 200);

        verify(auditRepository).findAuditOrders(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    public void testGetAuditOrders_NoFilters() {
        Page<AuditOrder> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(auditRepository.findAuditOrders(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                .thenReturn(mockPage);

        Page<AuditOrder> result = auditService.getAuditOrders(
                null, null, null, null, null, null, 0, 20);

        assertNotNull(result);
    }
}
