package com.neueda.leap.model.dto;
/**
 * Forgot password request DTO containing the username and email.
 * Java record has built in accessor methods for all fields. You can access it by using the field name as a method, e.g., `forgotPasswordRequest.username()`.
 */
public record ForgotPasswordRequest(
    String username,
    String email
) {
}
