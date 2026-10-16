package com.neueda.leap.repository;

import com.neueda.leap.model.CashTransactions;
import com.neueda.leap.model.dto.CashTransactionResult;
import java.util.Collection;
import java.time.LocalDate;
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
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
    """)
    List<CashTransactionResult> findAllTransactionHistory(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate BETWEEN :startDate AND :endDate
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
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
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 1 YEAR
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
    """)
    List<CashTransactionResult> findTransactionsPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 1 MONTH
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
    """)
    List<CashTransactionResult> findTransactionsPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 7 DAY
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
    """)
    List<CashTransactionResult> findTransactionsPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate >= CURRENT_DATE - 1 DAY
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
    """)
    List<CashTransactionResult> findTransactionsPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.CashTransactionResult(
            ct.cashTransactionId,
            ct.txnType,
            ct.amount,
            ct.txnDate,
            ct.createdAt
        )
        FROM CashTransactions ct
        JOIN ct.account a
        WHERE a.clientId = :clientId
            AND ct.txnDate = CURRENT_DATE
        ORDER BY ct.createdAt DESC, ct.cashTransactionId DESC
    """)
    List<CashTransactionResult> findTransactionsToday(@Param("clientId") Integer clientId);

    /** Deposits and withdrawals (the transactions not tied to an order) in the given accounts on or after a date. */
    List<CashTransactions> findByAccountIdInAndOrderIdIsNullAndTxnDateGreaterThanEqual(
            Collection<Integer> accountIds, LocalDate from);
}
