package com.neueda.leap.repository;

import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.dto.CashTransactionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for user cash transaction history by time period
 * User-specific: only returns transactions for authenticated user's accounts
 * Custom @Query methods return DTOs instead of managed entities
 */
public interface CashTransactionHistoryRepository extends JpaRepository<CashTransactions, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.transactionType,
            ct.amount,
            CAST(ct.transactionDate AS string)
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND CAST(ct.transactionDate AS date) BETWEEN :startDate AND :endDate
        ORDER BY ct.transactionDate DESC
    """)
    List<CashTransactionResult> findTransactionsByDateRange(
        @Param("clientId") Integer clientId,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.transactionType,
            ct.amount,
            CAST(ct.transactionDate AS string)
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND CAST(ct.transactionDate AS date) >= CURRENT_DATE - INTERVAL YEAR
        ORDER BY ct.transactionDate DESC
    """)
    List<CashTransactionResult> findTransactionsPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.transactionType,
            ct.amount,
            CAST(ct.transactionDate AS string)
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND CAST(ct.transactionDate AS date) >= CURRENT_DATE - INTERVAL MONTH
        ORDER BY ct.transactionDate DESC
    """)
    List<CashTransactionResult> findTransactionsPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.transactionType,
            ct.amount,
            CAST(ct.transactionDate AS string)
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND CAST(ct.transactionDate AS date) >= CURRENT_DATE - INTERVAL WEEK
        ORDER BY ct.transactionDate DESC
    """)
    List<CashTransactionResult> findTransactionsPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.transactionType,
            ct.amount,
            CAST(ct.transactionDate AS string)
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND CAST(ct.transactionDate AS date) >= CURRENT_DATE - INTERVAL DAY
        ORDER BY ct.transactionDate DESC
    """)
    List<CashTransactionResult> findTransactionsPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.transactionType,
            ct.amount,
            CAST(ct.transactionDate AS string)
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND CAST(ct.transactionDate AS date) = CURRENT_DATE
        ORDER BY ct.transactionDate DESC
    """)
    List<CashTransactionResult> findTransactionsToday(@Param("clientId") Integer clientId);
}
