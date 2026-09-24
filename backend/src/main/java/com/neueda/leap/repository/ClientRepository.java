package com.neueda.leap.repository;

import com.neueda.leap.model.Clients;
import com.neueda.leap.model.dto.ClientSegmentMetricsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ClientRepository extends JpaRepository<Clients, Integer> {

	@Query(value = """
		WITH client_portfolio AS (
			SELECT a.client_id,
				   COALESCE(SUM(av.total_value), 0) AS total_portfolio_value
			FROM accounts a
			LEFT JOIN account_valuations av ON av.account_id = a.account_id
			GROUP BY a.client_id
		),
		client_orders AS (
			SELECT a.client_id,
				   COUNT(o.order_id) AS recent_filled_order_count
			FROM accounts a
			LEFT JOIN orders o
				ON o.account_id = a.account_id
				AND o.order_status = 'Filled'
				AND o.submitted_at >= CURRENT_TIMESTAMP - (:lookbackDays * INTERVAL '1 day')
			GROUP BY a.client_id
		)
		SELECT c.client_id AS "clientId",
			   c.first_name AS "firstName",
			   c.last_name AS "lastName",
			   c.email AS "email",
			   COALESCE(cp.total_portfolio_value, 0) AS "totalPortfolioValue",
			   COALESCE(co.recent_filled_order_count, 0) AS "recentFilledOrderCount"
		FROM clients c
		LEFT JOIN client_portfolio cp ON cp.client_id = c.client_id
		LEFT JOIN client_orders co ON co.client_id = c.client_id
		ORDER BY c.last_name, c.first_name, c.client_id
	""", nativeQuery = true)
	List<ClientSegmentMetricsProjection> findClientSegmentMetrics(@Param("lookbackDays") Integer lookbackDays);
}