package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for verifying ownership
 * Verifies ownership of various entities for a given user
 */
@Service
public class OwnershipService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    
    public OwnershipService(UserRepository userRepository,
                            AccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    private Users validateUser(Integer userId) throws UserNotFoundException {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public void requireAccount(Integer accountId) throws UserNotFoundException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ForbiddenException("No authenticated user found");
        }

        Object details = authentication.getDetails();
        if (!(details instanceof Map<?, ?> detailsMap)) {
            throw new ForbiddenException("Authenticated user details are missing");
        }

        Object userIdValue = detailsMap.get("userId");
        if (!(userIdValue instanceof Integer userId) || userId <= 0) {
            throw new ForbiddenException("Authenticated user context is missing");
        }

        Users user = validateUser(userId);
        if (user.getClientId() == null || user.getClientId() <= 0) {
            throw new UserNotFoundException("User is not associated with a client");
        }

        if (!accountRepository.existsByAccountIdAndClientId(accountId, user.getClientId())) {
            throw new ForbiddenException("User does not own the specified account");
        }
    }
}
