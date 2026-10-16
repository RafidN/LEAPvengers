package com.neueda.leap.model.dto;

import java.util.List;

public class AuditOrderDetailsResponse {
    private AuditOrder order;
    private AuditClientSummary client;
    private AccountResponse account;
    private List<OrderEventResult> orderEvents;
    // The cash the fill moved (BR-15: what it settled as); null unless the order was FILLED
    private CashTransactionResult settlement;
    // Disputes the client raised about this order, newest first
    private List<DisputeResult> disputes = List.of();

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
    public List<DisputeResult> getDisputes() { return disputes; }
    public void setDisputes(List<DisputeResult> disputes) { this.disputes = disputes; }
    public CashTransactionResult getSettlement() { return settlement; }
    public void setSettlement(CashTransactionResult settlement) { this.settlement = settlement; }
    public List<OrderEventResult> getOrderEvents() { return orderEvents; }
    public void setOrderEvents(List<OrderEventResult> orderEvents) { this.orderEvents = orderEvents; }
}