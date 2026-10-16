package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.OrderDisputes;
import com.neueda.leap.model.Orders;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.DisputeRequest;
import com.neueda.leap.model.dto.DisputeResult;
import com.neueda.leap.repository.OrderDisputesRepository;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.security.SecurityContextHelper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Client disputes of their orders (BR-15): a trader raises one from their History page, Operations sees it
 * with the order's full record and resolves it with a written answer the trader then sees.
 */
@Service
public class DisputeService {
    static final Set<String> REASONS = Set.of("WRONG_PRICE", "SHOULD_HAVE_FILLED", "NOT_PLACED_BY_ME", "OTHER");
    static final int MAX_TEXT = 1000;

    private final OrderDisputesRepository disputesRepository;
    private final OrderRepository orderRepository;
    private final OwnershipService ownershipService;
    private final CurrentUserService currentUserService;
    private final SecurityContextHelper securityContextHelper;

    public DisputeService(OrderDisputesRepository disputesRepository, OrderRepository orderRepository,
                          OwnershipService ownershipService, CurrentUserService currentUserService,
                          SecurityContextHelper securityContextHelper) {
        this.disputesRepository = disputesRepository;
        this.orderRepository = orderRepository;
        this.ownershipService = ownershipService;
        this.currentUserService = currentUserService;
        this.securityContextHelper = securityContextHelper;
    }

    /** The signed-in trader disputes one of their own finished orders. */
    @Transactional
    public DisputeResult raise(Integer orderId, DisputeRequest request) {
        ownershipService.requireOrder(orderId);
        Users user = currentUserService.getCurrentUser();
        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim().toUpperCase(Locale.ROOT);
        if (!REASONS.contains(reason)) {
            throw new InvalidInputException("Choose what went wrong with the order");
        }
        String message = text(request.getMessage(), "Tell us what happened");

        Orders order = orderRepository.findById(orderId).orElseThrow(() -> new UserNotFoundException("Order not found"));
        if ("SUBMITTED".equals(order.getOrderStatus()) || "ACCEPTED".equals(order.getOrderStatus())) {
            throw new InvalidInputException("This order is still going through; you can dispute it once it has finished");
        }
        if (disputesRepository.existsByOrderIdAndStatus(orderId, "OPEN")) {
            throw new InvalidInputException("You already have an open dispute on this order");
        }
        try {
            OrderDisputes saved = disputesRepository.saveAndFlush(new OrderDisputes(orderId, user.getUserId(), reason, message));
            return disputesRepository.findResultById(saved.getDisputeId()).orElseThrow().forClient();
        } catch (DataIntegrityViolationException e) {
            // A second dispute raised at the same moment, past the check above
            throw new InvalidInputException("You already have an open dispute on this order");
        }
    }

    /** The signed-in trader's disputes, newest first. */
    @Transactional(readOnly = true)
    public List<DisputeResult> forCurrentClient() {
        Users user = currentUserService.getCurrentUser();
        return disputesRepository.findResultsByClientId(user.getClientId()).stream().map(DisputeResult::forClient).toList();
    }

    /** For Operations: every dispute, or only OPEN or RESOLVED ones. */
    @Transactional(readOnly = true)
    public List<DisputeResult> all(String status) {
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (wanted != null && !wanted.equals("OPEN") && !wanted.equals("RESOLVED")) {
            throw new InvalidInputException("Unsupported status: " + status + " (use OPEN or RESOLVED)");
        }
        return disputesRepository.findAllResults(wanted);
    }

    @Transactional(readOnly = true)
    public List<DisputeResult> forOrder(Integer orderId) {
        return disputesRepository.findResultsByOrderId(orderId);
    }

    /** Operations closes a dispute with a written answer for the client. */
    @Transactional
    public DisputeResult resolve(Integer disputeId, DisputeRequest request) {
        String resolution = text(request == null ? null : request.getResolution(), "Write what you found, for the client");
        OrderDisputes dispute = disputesRepository.findById(disputeId)
                .orElseThrow(() -> new UserNotFoundException("Dispute not found"));
        if (!"OPEN".equals(dispute.getStatus())) {
            throw new InvalidInputException("This dispute has already been resolved");
        }
        dispute.resolve(securityContextHelper.getUserIdFromContext(), resolution);
        disputesRepository.saveAndFlush(dispute);
        return disputesRepository.findResultById(disputeId).orElseThrow();
    }

    private static String text(String value, String missingMessage) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidInputException(missingMessage);
        }
        if (trimmed.length() > MAX_TEXT) {
            throw new InvalidInputException("Keep it under " + MAX_TEXT + " characters");
        }
        return trimmed;
    }
}
