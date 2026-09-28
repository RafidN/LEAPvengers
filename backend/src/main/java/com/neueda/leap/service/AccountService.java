package com.neueda.leap.service;

import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.Users;
import com.neueda.leap.model.dto.AccountResponse;
import com.neueda.leap.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {


    private final AccountRepository accountRepository;
    private final CurrentUserService currentUserService;


    public AccountService(AccountRepository accountRepository, CurrentUserService currentUserService){
        this.accountRepository = accountRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsForCurrentUser() {
        Users user = currentUserService.getCurrentUser();
        return accountRepository.findAccountResponsesByClientId(user.getClientId());
    }

}