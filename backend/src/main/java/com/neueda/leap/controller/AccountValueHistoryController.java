package com.neueda.leap.controller;

import com.neueda.leap.model.dto.AccountValuePoint;
import com.neueda.leap.service.AccountValueHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The signed-in trader's total account value over time (Trader only, like the rest of /accounts).
 * A bad period answers 400 through GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/accounts")
public class AccountValueHistoryController {

    private final AccountValueHistoryService accountValueHistoryService;

    public AccountValueHistoryController(AccountValueHistoryService accountValueHistoryService) {
        this.accountValueHistoryService = accountValueHistoryService;
    }

    /** ?period=1d|7d|1m|1y (optional, default 1m). Oldest point first; the last point is now. */
    @GetMapping("/value-history")
    public ResponseEntity<List<AccountValuePoint>> getValueHistory(
            @RequestParam(value = "period", required = false) String period) {
        return ResponseEntity.ok(accountValueHistoryService.getValueHistoryForCurrentUser(period));
    }
}
