package com.neueda.leap.service;

import com.neueda.leap.exception.ForbiddenException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Users getCurrentUser() {
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

        Users user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.getClientId() == null || user.getClientId() <= 0) {
            throw new UserNotFoundException("User is not associated with a client");
        }

        return user;
    }
}