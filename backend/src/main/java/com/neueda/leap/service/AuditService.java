package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.AuditClientDetailsResponse;
import com.neueda.leap.model.dto.AuditClientSummary;
import com.neueda.leap.model.dto.AuditOrder;
import com.neueda.leap.model.dto.AuditOrderDetailsResponse;
import com.neueda.leap.model.dto.OrderEventResult;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.AuditRepository;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.OrderEventsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service for audit order queries
 * Handles pagination logic and parameter validation
 */
@Service
public class AuditService {
    private static final int RECENT_ORDER_LIMIT = 10;

    private final AuditRepository auditRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final OrderEventsRepository orderEventsRepository;
    private final ClientSegmentService clientSegmentService;

    public AuditService(AuditRepository auditRepository,
                        ClientRepository clientRepository,
                        AccountRepository accountRepository,
                        OrderEventsRepository orderEventsRepository,
                        ClientSegmentService clientSegmentService) {
        this.auditRepository = auditRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.orderEventsRepository = orderEventsRepository;
        this.clientSegmentService = clientSegmentService;
    }

        public AuditOrderDetailsResponse getAuditById(Integer orderId) {
        AuditOrder order = auditRepository.findAuditOrderById(orderId)
            .orElseThrow(() -> new UserNotFoundException("Order not found"));
        AuditClientSummary client = clientRepository.findAuditClientSummaryByClientId(order.getClientId())
            .map(summary -> withCurrentSegment(summary, clientSegmentService.getCurrentSegments()))
            .orElseThrow(() -> new UserNotFoundException("Client not found"));
        AccountResponse account = accountRepository.findAccountResponseByOrderId(orderId)
            .orElseThrow(() -> new UserNotFoundException("Account not found"));
        List<OrderEventResult> events = orderEventsRepository.findAuditOrderEventsByOrderId(orderId);
        return new AuditOrderDetailsResponse(order, client, account, events);
        }

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

    public List<AuditClientSummary> searchClients(String query) {
        List<AuditClientSummary> clients = clientRepository.searchAuditClients(normalizeQuery(query));
        if (clients.isEmpty()) {
            return clients;
        }
        Map<Integer, String> segments = clientSegmentService.getCurrentSegments();
        return clients.stream().map(client -> withCurrentSegment(client, segments)).toList();
    }

    public AuditClientDetailsResponse getClientDetails(Integer clientId) {
        AuditClientSummary client = clientRepository.findAuditClientSummaryByClientId(clientId)
                .map(summary -> withCurrentSegment(summary, clientSegmentService.getCurrentSegments()))
                .orElseThrow(() -> new UserNotFoundException("Client not found"));

        List<AccountResponse> accounts = accountRepository.findAccountResponsesByClientId(clientId);
        List<AuditOrder> recentOrders = auditRepository.findAuditOrders(
                clientId,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, RECENT_ORDER_LIMIT))
                .getContent();

        return new AuditClientDetailsResponse(
                client.getClientId(),
                client.getFirstName(),
                client.getLastName(),
                client.getEmail(),
                client.getSegment(),
                accounts,
                recentOrders);
    }

    /**
     * Swaps in the client's current segment, computed the same way as the Clients page. The stored
     * clients.segment is only refreshed when that page is opened, so it can be out of date.
     */
    private AuditClientSummary withCurrentSegment(AuditClientSummary client, Map<Integer, String> segments) {
        String segment = segments.get(client.getClientId());
        if (segment != null) {
            client.setSegment(segment);
        }
        return client;
    }

    /**
     * No query becomes "", which matches every client (LIKE '%%'). A null would be sent to Postgres
     * as an untyped parameter, and LIKE on it fails ("operator does not exist: text ~~ bytea").
     */
    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }
}
