package com.neueda.leap.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Helper to extract authenticated user details from the security context.
 * Avoids repeated JWT parsing by reading already-populated authentication.
 */
@Component
public class SecurityContextHelper {
    /**
     * Extracts the userId from the current security context.
     * The token is already validated and parsed by JwtAuthenticationFilter.
     */
    public Integer getUserIdFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Map)) {
            return null;
        }
        Map<String, Object> details = (Map<String, Object>) auth.getDetails();
        return (Integer) details.get("userId");
    }

    /**
     * Extracts the user's role from the security context.
     */
    public String getRoleFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Map)) {
            return null;
        }
        Map<String, Object> details = (Map<String, Object>) auth.getDetails();
        return (String) details.get("role");
    }

    /**
     * Extracts the clientId from the security context.
     */
    public Integer getClientIdFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Map)) {
            return null;
        }
        Map<String, Object> details = (Map<String, Object>) auth.getDetails();
        return (Integer) details.get("clientId");
    }

    /**
     * Extracts all user details (userId, clientId, username, role) from context.
     */
    public Map<String, Object> getUserDetailsFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getDetails() instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) auth.getDetails();
    }
}
