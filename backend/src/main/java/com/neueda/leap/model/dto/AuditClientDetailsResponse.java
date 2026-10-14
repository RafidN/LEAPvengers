package com.neueda.leap.model.dto;

import java.util.List;

public class AuditClientDetailsResponse extends AuditClientSummary {
    private List<AccountResponse> accounts;
    private List<AuditOrder> recentOrders;

    public AuditClientDetailsResponse(Integer clientId,
                                      String firstName,
                                      String lastName,
                                      String email,
                                      String segment,
                                      List<AccountResponse> accounts,
                                      List<AuditOrder> recentOrders) {
        super(clientId, firstName, lastName, email, segment);
        this.accounts = accounts;
        this.recentOrders = recentOrders;
    }

    public List<AccountResponse> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<AccountResponse> accounts) {
        this.accounts = accounts;
    }

    public List<AuditOrder> getRecentOrders() {
        return recentOrders;
    }

    public void setRecentOrders(List<AuditOrder> recentOrders) {
        this.recentOrders = recentOrders;
    }
}