package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.AuditClientDetailsResponse;
import com.neueda.leap.model.dto.AuditClientSummary;
import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.AuditRepository;
import com.neueda.leap.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

        @Mock
        private ClientRepository clientRepository;

        @Mock
        private AccountRepository accountRepository;

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

        @Test
        public void getClientDetailsAggregatesProfileAccountsAndRecentOrders() {
                AuditClientSummary client = new AuditClientSummary(7, "Ada", "Lovelace", "ada@example.com", "Premier");
                List<AccountResponse> accounts = List.of(
                                new AccountResponse(1001, LocalDate.of(2024, 1, 10), new BigDecimal("1500.25")));
                AuditOrder recentOrder = new AuditOrder(
                                55,
                                7,
                                "Ada Lovelace",
                                "AAPL",
                                "Apple Inc.",
                                "BUY",
                                new BigDecimal("10.0000"),
                                new BigDecimal("185.5000"),
                                "FILLED",
                                null,
                                LocalDate.of(2024, 2, 1),
                                null,
                                null);

                when(clientRepository.findAuditClientSummaryByClientId(7)).thenReturn(Optional.of(client));
                when(accountRepository.findAccountResponsesByClientId(7)).thenReturn(accounts);
                when(auditRepository.findAuditOrders(eq(7), eq(null), eq(null), eq(null), eq(null), eq(null), any()))
                                .thenReturn(new PageImpl<>(List.of(recentOrder), PageRequest.of(0, 10), 1));

                AuditClientDetailsResponse response = auditService.getClientDetails(7);

                assertEquals(7, response.getClientId());
                assertEquals("Premier", response.getSegment());
                assertEquals(accounts, response.getAccounts());
                assertEquals(1, response.getRecentOrders().size());
                assertEquals("AAPL", response.getRecentOrders().get(0).getTicker());
        }

        @Test
        public void getClientDetailsThrowsWhenClientMissing() {
                when(clientRepository.findAuditClientSummaryByClientId(99)).thenReturn(Optional.empty());

                assertThrows(UserNotFoundException.class, () -> auditService.getClientDetails(99));
        }

        @Test
        public void searchClientsNormalizesBlankQuery() {
                when(clientRepository.searchAuditClients(null)).thenReturn(List.of());

                List<AuditClientSummary> results = auditService.searchClients("   ");

                assertTrue(results.isEmpty());
                verify(clientRepository).searchAuditClients(null);
        }
}
