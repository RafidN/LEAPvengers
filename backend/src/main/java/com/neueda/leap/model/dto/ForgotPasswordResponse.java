package com.neueda.leap.model.dto;
/**
 * Forgot password response DTO containing the status and message.
 * Java record has built in accessor methods for all fields. You can access it by using the field name as a method, e.g., `forgotPasswordResponse.status()`.
 */
public record ForgotPasswordResponse(
    String status,
    String message
) {
}
