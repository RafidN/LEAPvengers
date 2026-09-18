package com.neueda.leap.repository;

import com.neueda.leap.model.Orders;
import com.neueda.leap.model.dto.OrderHistoryResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for user order history by time period
 * User-specific: only returns orders for authenticated user's accounts
 * Custom @Query methods return DTOs instead of managed entities
 */
public interface OrderHistoryRepository extends JpaRepository<Orders, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            CAST(o.quantity AS java.lang.Long),
            o.price,
            o.orderStatus,
            CAST(o.orderDate AS string),
            CAST(o.submittedAt AS string)
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND CAST(o.orderDate AS date) BETWEEN :startDate AND :endDate
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersByDateRange(
        @Param("clientId") Integer clientId,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            CAST(o.quantity AS java.lang.Long),
            o.price,
            o.orderStatus,
            CAST(o.orderDate AS string),
            CAST(o.submittedAt AS string)
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND CAST(o.orderDate AS date) >= CURRENT_DATE - INTERVAL YEAR
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            CAST(o.quantity AS java.lang.Long),
            o.price,
            o.orderStatus,
            CAST(o.orderDate AS string),
            CAST(o.submittedAt AS string)
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND CAST(o.orderDate AS date) >= CURRENT_DATE - INTERVAL MONTH
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            CAST(o.quantity AS java.lang.Long),
            o.price,
            o.orderStatus,
            CAST(o.orderDate AS string),
            CAST(o.submittedAt AS string)
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND CAST(o.orderDate AS date) >= CURRENT_DATE - INTERVAL WEEK
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            CAST(o.quantity AS java.lang.Long),
            o.price,
            o.orderStatus,
            CAST(o.orderDate AS string),
            CAST(o.submittedAt AS string)
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND CAST(o.orderDate AS date) >= CURRENT_DATE - INTERVAL DAY
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.OrderHistoryResult(
            o.orderId,
            i.ticker,
            o.orderType,
            CAST(o.quantity AS java.lang.Long),
            o.price,
            o.orderStatus,
            CAST(o.orderDate AS string),
            CAST(o.submittedAt AS string)
        )
        FROM Orders o
        JOIN o.instrument i
        JOIN o.account a
        WHERE a.clientId = :clientId
            AND CAST(o.orderDate AS date) = CURRENT_DATE
        ORDER BY o.submittedAt DESC
    """)
    List<OrderHistoryResult> findOrdersToday(@Param("clientId") Integer clientId);
}
