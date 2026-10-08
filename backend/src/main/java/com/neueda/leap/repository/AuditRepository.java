package com.neueda.leap.repository;

import com.neueda.leap.model.dto.AuditOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.neueda.leap.model.Orders;

import java.time.LocalDate;


@Repository
public interface AuditRepository extends JpaRepository<Orders, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.AuditOrder(
            o.orderId,
            c.clientId,
            CONCAT(c.firstName, ' ', c.lastName),
            i.ticker,
            i.instrumentName,
            o.orderType,
            o.quantity,
            o.price,
            o.orderStatus,
            o.rejectionReason,
            o.orderDate,
            o.submittedAt,
            o.executedAt
        )
        FROM Orders o
        JOIN o.account a
        JOIN a.client c
        JOIN o.instrument i
        WHERE (:clientId IS NULL OR c.clientId = :clientId)
            AND (:ticker IS NULL OR i.ticker = :ticker)
            AND (:orderStatus IS NULL OR o.orderStatus = :orderStatus)
            AND (:rejectionReason IS NULL OR o.rejectionReason = :rejectionReason)
            AND (:startDate IS NULL OR o.orderDate >= :startDate)
            AND (:endDate IS NULL OR o.orderDate <= :endDate)
        ORDER BY o.submittedAt DESC
    """)
    Page<AuditOrder> findAuditOrders(
        @Param("clientId") Integer clientId,
        @Param("ticker") String ticker,
        @Param("orderStatus") String orderStatus,
        @Param("rejectionReason") String rejectionReason,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );
}

