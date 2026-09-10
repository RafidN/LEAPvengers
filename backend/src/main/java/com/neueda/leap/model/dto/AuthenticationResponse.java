package com.neueda.leap.model.dto;

public class AuthenticationResponse {
    private String token;
    private Integer userId;
    private String username;
    private Integer clientId;
    private String email;

    public AuthenticationResponse() {
    }

    public AuthenticationResponse(String token, Integer userId, String username, Integer clientId, String email) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.clientId = clientId;
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}