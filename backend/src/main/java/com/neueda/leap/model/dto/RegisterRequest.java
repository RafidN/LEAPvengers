package com.neueda.leap.model.dto;
/**
 * Register request DTO containing the first name, last name, email, username, and password.
 * Java record has built in accessor methods for all fields. You can access it by using the field name as a method, e.g., `registerRequest.firstName()`.
 */
public record RegisterRequest(
    String firstName,
    String lastName,
    String email,
    String username,
    String password
) {
}
