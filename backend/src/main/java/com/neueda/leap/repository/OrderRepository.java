package com.neueda.leap.repository;

import com.neueda.leap.model.Orders;
import com.neueda.leap.model.dto.OrderHistoryResult;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalDate;
/**
 * Repository for user order history by time period
 * User-specific: only returns orders for authenticated user's accounts
 * Custom @Query methods return DTOs instead of managed entities
 */
public interface OrderRepository extends JpaRepository<Orders, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findAllOrderHistory(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND o.orderDate BETWEEN :startDate AND :endDate
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersByDateRange(
        @Param("clientId") Integer clientId,
        @Param("startDate") java.time.LocalDate startDate,
        @Param("endDate") java.time.LocalDate endDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND o.orderDate >= CURRENT_DATE - 1 YEAR
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND o.orderDate >= CURRENT_DATE - 1 MONTH
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND o.orderDate >= CURRENT_DATE - 7 DAY
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND o.orderDate >= CURRENT_DATE - 1 DAY
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.orderDate,
            o.submittedAt,
            o.accountId,
            o.rejectionReason,
            o.executionPrice,
            o.executedAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND o.orderDate = CURRENT_DATE
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersToday(@Param("clientId") Integer clientId);

    @Query("""
        SELECT COUNT(o) > 0
        FROM Orders o
        JOIN o.account a
        WHERE o.orderId = :orderId
            AND a.clientId = :clientId
    """)
    boolean existsByOrderIdAndClientId(
        @Param("orderId") Integer orderId,
        @Param("clientId") Integer clientId
    );

    /**
     * The order, locked until the transaction ends (SELECT ... FOR UPDATE). The fill timer and the restart
     * recovery sweep can reach the same order; whichever comes second waits here, then sees it is no longer
     * ACCEPTED, so an order is never filled twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Orders o WHERE o.orderId = :orderId")
    Optional<Orders> findByIdForUpdate(@Param("orderId") Integer orderId);

    /** Orders still in this status that were submitted before a moment, oldest first: ones the platform lost track of. */
    @Query("SELECT o.orderId FROM Orders o WHERE o.orderStatus = :status AND o.submittedAt < :before ORDER BY o.submittedAt")
    List<Integer> findIdsByStatusSubmittedBefore(@Param("status") String status, @Param("before") LocalDateTime before);

    /** Orders in the given accounts with this status that executed after a moment, e.g. FILLED since a week ago. */
    List<Orders> findByAccountIdInAndOrderStatusAndExecutedAtAfter(
            Collection<Integer> accountIds, String orderStatus, LocalDateTime after);

    /**
     * Every order filled after a moment, oldest first, with what the reporting store needs. The only read of
     * live orders the reporting side makes: one small batch per load (ReportingLoadService), never per report.
     */
    @Query("""
        SELECT o.orderId AS orderId, o.executedAt AS executedAt, a.clientId AS clientId,
               o.instrumentId AS instrumentId, i.ticker AS ticker, i.assetClass AS assetClass, i.market AS market,
               o.orderType AS side, o.quantity AS quantity, o.executionPrice AS price
        FROM Orders o
        JOIN o.account a
        JOIN o.instrument i
        WHERE o.orderStatus = 'FILLED' AND o.executedAt > :since
        ORDER BY o.executedAt
    """)
    List<com.neueda.leap.model.dto.FilledOrderRow> findFilledSince(@Param("since") LocalDateTime since);
}
