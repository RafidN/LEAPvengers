package com.neueda.leap.controller;

import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.model.dto.AccountPortfolioResponse;
import com.neueda.leap.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class AccountsController {

    private final AccountService accountService;

    public AccountsController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts() {
        return ResponseEntity.ok(accountService.getAccountsForCurrentUser());
    }

    @GetMapping("/{id}/portfolio")
    public ResponseEntity<AccountPortfolioResponse> getPortfolio(@PathVariable("id") Integer accountId) {
        return ResponseEntity.ok(accountService.getPortfolioForAccount(accountId));
    }
}
