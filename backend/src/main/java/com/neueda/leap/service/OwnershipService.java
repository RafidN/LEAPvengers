package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.OrderRepository;
import org.springframework.stereotype.Service;

/**
 * Service for verifying ownership
 * Verifies ownership of various entities for a given user
 */
@Service
public class OwnershipService {

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final CurrentUserService currentUserService;
    
    public OwnershipService(AccountRepository accountRepository,
                            OrderRepository orderRepository,
                            CurrentUserService currentUserService) {
        this.accountRepository = accountRepository;
        this.orderRepository = orderRepository;
        this.currentUserService = currentUserService;
    }

    public void requireAccount(Integer accountId) {
        Users user = currentUserService.getCurrentUser();
        if (!accountRepository.existsByAccountIdAndClientId(accountId, user.getClientId())) {
            throw new ForbiddenException("User does not own the specified account");
        }
    }

    public void requireOrder(Integer orderId) {
        Users user = currentUserService.getCurrentUser();
        if (!orderRepository.existsByOrderIdAndClientId(orderId, user.getClientId())) {
            throw new ForbiddenException("User does not own the specified order");
        }
    }
}
