package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountHoldingResponse;
import com.neueda.leap.model.dto.AccountPortfolioResponse;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.repository.AccountRepository;
import com.neueda.leap.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class AccountService {


    private final AccountRepository accountRepository;
    private final CurrentUserService currentUserService;
    private final PortfolioRepository portfolioRepository;
    private final OwnershipService ownershipService;


    public AccountService(AccountRepository accountRepository,
                          CurrentUserService currentUserService,
                          PortfolioRepository portfolioRepository,
                          OwnershipService ownershipService) {
        this.accountRepository = accountRepository;
        this.currentUserService = currentUserService;
        this.portfolioRepository = portfolioRepository;
        this.ownershipService = ownershipService;
    }

    @Transactional(readOnly = true)
    public AccountPortfolioResponse getPortfolioForAccount(Integer accountId) {
        ownershipService.requireAccount(accountId);

        Accounts account = accountRepository.findById(accountId)
            .orElseThrow(() -> new UserNotFoundException("Account not found"));

        List<AccountHoldingResponse> holdings = portfolioRepository.findAccountHoldings(accountId);

        BigDecimal holdingsValue = holdings.stream()
            .map(AccountHoldingResponse::getValue)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBalance = account.getBalance().add(holdingsValue);

        return new AccountPortfolioResponse(
            account.getAccountId(),
            account.getBalance(),
            holdings,
            totalBalance
        );
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForCurrentUser() {
        Users user = currentUserService.getCurrentUser();
        return accountRepository.findAccountResponsesByClientId(user.getClientId());
    }

    @Transactional(readOnly = true)
    public List<AccountHoldingResponse> getAccountHoldingsForCurrentUser(Integer accountId) {
        ownershipService.requireAccount(accountId);
        return portfolioRepository.findAccountHoldings(accountId);
    }
}