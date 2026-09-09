package com.neueda.leap.model.dto;

/**
 * Authentication response DTO containing the token, user ID, username, client ID, and email.
 * Java record has built in accessor methods for all fields. You can access it by using the field name as a method, e.g., `authenticationResponse.token()`.
 */
public record AuthenticationResponse(
    String token,
    Integer userId,
    String username,
    Integer clientId,
    String email
) {
}
