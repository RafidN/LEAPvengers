package com.neueda.leap.repository;

import com.neueda.leap.model.Orders;
import com.neueda.leap.model.dto.OrderStatusCountByDateProjection;
import com.neueda.leap.model.dto.OrderStatusCountProjection;
import com.neueda.leap.model.dto.OrderHistoryResult;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
        SELECT o.orderStatus AS orderStatus, COUNT(o) AS count
        FROM Orders o
        WHERE o.orderDate BETWEEN :startDate AND :endDate
        GROUP BY o.orderStatus
    """)
    List<OrderStatusCountProjection> countByStatusBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT o.orderDate AS orderDate, o.orderStatus AS orderStatus, COUNT(o) AS count
        FROM Orders o
        WHERE o.orderDate BETWEEN :startDate AND :endDate
        GROUP BY o.orderDate, o.orderStatus
        ORDER BY o.orderDate
    """)
    List<OrderStatusCountByDateProjection> countByStatusByDateBetween(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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
            o.executedAt,
            o.orderKind,
            o.triggerPrice,
            o.triggeredAt
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

    /** The order, locked as above, or empty if another transaction holds it (SELECT ... FOR UPDATE SKIP LOCKED). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT o FROM Orders o WHERE o.orderId = :orderId")
    Optional<Orders> findByIdForUpdateSkipLocked(@Param("orderId") Integer orderId);

    /**
     * Orders still in this status that were submitted before a moment, oldest first: ones the platform lost track
     * of. A limit or stop order counts from when its price was reached, since it only started filling then.
     */
    @Query("""
        SELECT o.orderId FROM Orders o
        WHERE o.orderStatus = :status AND COALESCE(o.triggeredAt, o.submittedAt) < :before
        ORDER BY o.submittedAt
    """)
    List<Integer> findIdsByStatusSubmittedBefore(@Param("status") String status, @Param("before") LocalDateTime before);

    /**
     * WORKING orders whose price the latest quote has reached, oldest first: a limit buy or stop sell when the
     * price is at or below its trigger price, a limit sell or stop buy when it is at or above it.
     */
    @Query("""
        SELECT o.orderId FROM Orders o, LatestPriceQuotes q
        WHERE q.instrumentId = o.instrumentId
            AND o.orderStatus = 'WORKING'
            AND (((o.orderKind = 'LIMIT' AND o.orderType = 'BUY') OR (o.orderKind = 'STOP' AND o.orderType = 'SELL'))
                    AND q.price <= o.triggerPrice
                OR ((o.orderKind = 'LIMIT' AND o.orderType = 'SELL') OR (o.orderKind = 'STOP' AND o.orderType = 'BUY'))
                    AND q.price >= o.triggerPrice)
        ORDER BY o.submittedAt
    """)
    List<Integer> findWorkingOrderIdsAtTriggerPrice();

    /**
     * Cash the account's WORKING buy orders could spend: quantity x limit or stop price, summed. An order is left
     * out by id, e.g. the one being replaced; pass null to count them all.
     */
    @Query("""
        SELECT COALESCE(SUM(o.quantity * o.triggerPrice), 0) FROM Orders o
        WHERE o.accountId = :accountId AND o.orderStatus = 'WORKING' AND o.orderType = 'BUY'
            AND (:excludedOrderId IS NULL OR o.orderId <> :excludedOrderId)
    """)
    BigDecimal cashHeldForWorkingBuys(@Param("accountId") Integer accountId,
                                      @Param("excludedOrderId") Integer excludedOrderId);

    /** The account's WORKING sell orders for an instrument that are for more than quantity. */
    @Query("""
        SELECT o.orderId FROM Orders o
        WHERE o.accountId = :accountId AND o.instrumentId = :instrumentId AND o.orderStatus = 'WORKING'
            AND o.orderType = 'SELL' AND o.quantity > :quantity
        ORDER BY o.submittedAt
    """)
    List<Integer> findWorkingSellIdsLargerThan(@Param("accountId") Integer accountId,
                                               @Param("instrumentId") Integer instrumentId,
                                               @Param("quantity") BigDecimal quantity);

    /** The client's WORKING orders across their accounts, oldest first. */
    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId, i.ticker, o.orderType, o.quantity, o.price, o.orderStatus, o.orderDate, o.submittedAt,
            o.accountId, o.rejectionReason, o.executionPrice, o.executedAt, o.orderKind, o.triggerPrice, o.triggeredAt
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId AND o.orderStatus = 'WORKING'
        ORDER BY o.submittedAt
    """)
    List<OrderHistoryResult> findWorkingOrders(@Param("clientId") Integer clientId);

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
