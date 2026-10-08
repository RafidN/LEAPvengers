package com.neueda.leap.repository;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.dto.AccountResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AccountRepository extends JpaRepository<Accounts, Integer> {
    boolean existsByAccountIdAndClientId(Integer accountId, Integer clientId);

    @Query("""
            SELECT new com.neueda.leap.model.dto.AccountResponse(
                a.accountId,
                a.openedDate,
                a.balance
            )
            FROM Accounts a
            WHERE a.clientId = :clientId
            ORDER BY a.openedDate ASC, a.accountId ASC
            """)
    List<AccountResponse> findAccountResponsesByClientId(Integer clientId);

   @Query("""
            SELECT new com.neueda.leap.model.dto.AccountResponse(
                a.accountId,
                a.openedDate,
                a.balance
            )
            FROM Accounts a
            WHERE a.accountId = :accountId
            ORDER BY a.openedDate ASC, a.accountId ASC
            """)
    List<AccountResponse> findAccountResponsesByAccountId(Integer accountId);
}
