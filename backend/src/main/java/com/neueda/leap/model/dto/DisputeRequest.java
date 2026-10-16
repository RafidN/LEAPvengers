package com.neueda.leap.model.dto;

/**
 * Body for raising a dispute (reason and message) or resolving one (resolution). Unused fields are ignored.
 */
public class DisputeRequest {
    private String reason;
    private String message;
    private String resolution;

    public DisputeRequest() {
    }

    public DisputeRequest(String reason, String message, String resolution) {
        this.reason = reason;
        this.message = message;
        this.resolution = resolution;
    }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
