package com.neueda.leap.repository;

import com.neueda.leap.model.OrderEvents;
import com.neueda.leap.model.dto.OrderEventResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for order event history queries.
 * User-specific: only returns events for orders owned by the authenticated user's client.
 * Custom @Query methods return DTOs instead of managed entities.
 */
public interface OrderEventsRepository extends JpaRepository<OrderEvents, Integer> {

	@Query("""
		SELECT new com.neueda.leap.model.dto.OrderEventResult(
			oe.eventId,
			oe.orderId,
			oe.createdBy,
			u.username,
			oe.fromStatus,
			oe.toStatus,
			oe.createdAt,
			oe.details
		)
		FROM OrderEvents oe
		JOIN oe.order o
		JOIN o.account a
		LEFT JOIN oe.user u
		WHERE a.clientId = :clientId
			AND oe.orderId = :orderId
		ORDER BY oe.createdAt DESC, oe.eventId DESC
	""")
	List<OrderEventResult> findOrderEventsByOrderId(
		@Param("clientId") Integer clientId,
		@Param("orderId") Integer orderId
	);

}
