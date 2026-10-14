package com.neueda.leap.model.dto;

import java.util.List;

public class AuditOrderDetailsResponse {
    private AuditOrder order;
    private AuditClientSummary client;
    private AccountResponse account;
    private List<OrderEventResult> orderEvents;

    public AuditOrderDetailsResponse() {
    }

    public AuditOrderDetailsResponse(AuditOrder order, AuditClientSummary client,
                                     AccountResponse account, List<OrderEventResult> orderEvents) {
        this.order = order;
        this.client = client;
        this.account = account;
        this.orderEvents = orderEvents;
    }

    public AuditOrder getOrder() { return order; }
    public void setOrder(AuditOrder order) { this.order = order; }
    public AuditClientSummary getClient() { return client; }
    public void setClient(AuditClientSummary client) { this.client = client; }
    public AccountResponse getAccount() { return account; }
    public void setAccount(AccountResponse account) { this.account = account; }
    public List<OrderEventResult> getOrderEvents() { return orderEvents; }
    public void setOrderEvents(List<OrderEventResult> orderEvents) { this.orderEvents = orderEvents; }
}