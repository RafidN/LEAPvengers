package com.neueda.leap.model.dto;

import java.math.BigDecimal;

/**
 * Response DTO for client segmentation queries.
 */
public class ClientSegmentResult {
    private Integer clientId;
    private String firstName;
    private String lastName;
    private String email;
    private BigDecimal totalPortfolioValue;
    private Long recentFilledOrderCount;
    private String segment;

    public ClientSegmentResult() {
    }

    public ClientSegmentResult(Integer clientId, String firstName, String lastName, String email,
                               BigDecimal totalPortfolioValue, Long recentFilledOrderCount, String segment) {
        this.clientId = clientId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.totalPortfolioValue = totalPortfolioValue;
        this.recentFilledOrderCount = recentFilledOrderCount;
        this.segment = segment;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getTotalPortfolioValue() {
        return totalPortfolioValue;
    }

    public void setTotalPortfolioValue(BigDecimal totalPortfolioValue) {
        this.totalPortfolioValue = totalPortfolioValue;
    }

    public Long getRecentFilledOrderCount() {
        return recentFilledOrderCount;
    }

    public void setRecentFilledOrderCount(Long recentFilledOrderCount) {
        this.recentFilledOrderCount = recentFilledOrderCount;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }
}