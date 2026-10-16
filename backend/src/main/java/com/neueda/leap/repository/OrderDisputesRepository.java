package com.neueda.leap.repository;

import com.neueda.leap.model.OrderDisputes;
import com.neueda.leap.model.dto.DisputeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Disputes with their order, client and the people involved, newest first. The ON conditions repeat the
 * column joins because OrderDisputes keeps plain ids rather than relationships.
 */
public interface OrderDisputesRepository extends JpaRepository<OrderDisputes, Integer> {

    String RESULT = """
        SELECT new com.neueda.leap.model.dto.DisputeResult(
            d.disputeId, o.orderId, c.clientId, CONCAT(c.firstName, ' ', c.lastName), i.ticker,
            o.orderType, o.quantity, o.orderStatus, d.reason, d.message, d.status, d.createdAt,
            raiser.username, d.resolution, d.resolvedAt, resolver.username)
        FROM OrderDisputes d
        JOIN Orders o ON o.orderId = d.orderId
        JOIN o.account a
        JOIN a.client c
        JOIN o.instrument i
        JOIN Users raiser ON raiser.userId = d.raisedBy
        LEFT JOIN Users resolver ON resolver.userId = d.resolvedBy
        """;

    /** Every dispute, or only OPEN or RESOLVED ones, for Operations. */
    @Query(RESULT + " WHERE (:status IS NULL OR d.status = :status) ORDER BY d.createdAt DESC")
    List<DisputeResult> findAllResults(@Param("status") String status);

    /** One client's disputes. */
    @Query(RESULT + " WHERE c.clientId = :clientId ORDER BY d.createdAt DESC")
    List<DisputeResult> findResultsByClientId(@Param("clientId") Integer clientId);

    /** One order's disputes. */
    @Query(RESULT + " WHERE d.orderId = :orderId ORDER BY d.createdAt DESC")
    List<DisputeResult> findResultsByOrderId(@Param("orderId") Integer orderId);

    @Query(RESULT + " WHERE d.disputeId = :disputeId")
    Optional<DisputeResult> findResultById(@Param("disputeId") Integer disputeId);

    boolean existsByOrderIdAndStatus(Integer orderId, String status);
}
