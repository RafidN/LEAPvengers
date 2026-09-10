package com.neueda.leap.model.dto;

public class ForgotPasswordResponse {
    private String status;
    private String message;

    public ForgotPasswordResponse() {
    }

    public ForgotPasswordResponse(String status, String message) {
        this.status = status;
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}