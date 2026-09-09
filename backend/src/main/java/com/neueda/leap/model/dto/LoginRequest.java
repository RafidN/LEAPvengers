package com.neueda.leap.model.dto;
/**
 * Login request DTO containing the username and password.
 * Java record has built in accessor methods for all fields. You can access it by using the field name as a method, e.g., `loginRequest.username()`.
 */
public record LoginRequest(
    String username,
    String password
) {
}
