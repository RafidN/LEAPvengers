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
public interface CashTransactionRepository extends JpaRepository<CashTransactions, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate BETWEEN :startDate AND :endDate
        ORDER BY ct.txnDate DESC
    """)
    List<CashTransactionResult> findTransactionsByDateRange(
        @Param("clientId") Integer clientId,
        @Param("startDate") java.time.LocalDate startDate,
        @Param("endDate") java.time.LocalDate endDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 1 YEAR
        ORDER BY ct.txnDate DESC
    """)
    List<CashTransactionResult> findTransactionsPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 1 MONTH
        ORDER BY ct.txnDate DESC
    """)
    List<CashTransactionResult> findTransactionsPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 7 DAY
        ORDER BY ct.txnDate DESC
    """)
    List<CashTransactionResult> findTransactionsPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 1 DAY
        ORDER BY ct.txnDate DESC
    """)
    List<CashTransactionResult> findTransactionsPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate = CURRENT_DATE
        ORDER BY ct.txnDate DESC
    """)
    List<CashTransactionResult> findTransactionsToday(@Param("clientId") Integer clientId);
}
