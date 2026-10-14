package com.neueda.leap.repository;

import com.neueda.leap.model.Accounts;
import com.neueda.leap.model.dto.AccountResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Accounts, Integer> {
    boolean existsByAccountIdAndClientId(Integer accountId, Integer clientId);

    /** Loads an account and locks its row until the transaction ends, so fills on one account run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Accounts a WHERE a.accountId = :accountId")
    Optional<Accounts> findByIdForUpdate(Integer accountId);

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

    @Query("""
            SELECT new com.neueda.leap.model.dto.AccountResponse(a.accountId, a.openedDate, a.balance)
            FROM Accounts a JOIN Orders o ON o.accountId = a.accountId
            WHERE o.orderId = :orderId
            """)
    Optional<AccountResponse> findAccountResponseByOrderId(Integer orderId);
}
