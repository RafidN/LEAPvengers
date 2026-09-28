package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.AccountRepository;
import org.springframework.stereotype.Service;

/**
 * Service for verifying ownership
 * Verifies ownership of various entities for a given user
 */
@Service
public class OwnershipService {

    private final AccountRepository accountRepository;
    private final CurrentUserService currentUserService;
    
    public OwnershipService(AccountRepository accountRepository,
                            CurrentUserService currentUserService) {
        this.accountRepository = accountRepository;
        this.currentUserService = currentUserService;
    }

    public void requireAccount(Integer accountId) {
        Users user = currentUserService.getCurrentUser();
        if (!accountRepository.existsByAccountIdAndClientId(accountId, user.getClientId())) {
            throw new ForbiddenException("User does not own the specified account");
        }
    }
}
