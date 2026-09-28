package com.neueda.leap.repository;

import com.neueda.leap.model.dto.AccountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void findAccountResponsesByClientIdReturnsSeededTraderAccount() {
        List<AccountResponse> responses = accountRepository.findAccountResponsesByClientId(1);

        assertEquals(1, responses.size());

        AccountResponse response = responses.get(0);
        assertEquals(1, response.getAccountId());
        assertEquals(LocalDate.of(2026, 1, 1), response.getOpenedDate());
        assertEquals(0, response.getBalance().compareTo(new BigDecimal("100000.00")));
    }
}