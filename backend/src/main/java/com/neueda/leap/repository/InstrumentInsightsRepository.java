package com.neueda.leap.repository;

import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.dto.InstrumentInsightsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface InstrumentInsightsRepository extends JpaRepository<Holdings, Integer> {

    @Query(value = """
        WITH client_portfolio AS (
            SELECT a.client_id,
                   COALESCE(SUM(av.total_value), 0) AS total_portfolio_value
            FROM accounts a
            LEFT JOIN account_valuations av ON av.account_id = a.account_id
            GROUP BY a.client_id
        ),
        -- Trading history comes from the reporting store, not the live orders table (BR-16)
        client_orders AS (
            SELECT t.client_id,
                   COUNT(*) AS recent_filled_order_count
            FROM reporting.trade_activity t
            WHERE t.executed_at >= CURRENT_TIMESTAMP - (:lookbackDays * INTERVAL '1 day')
            GROUP BY t.client_id
        ),
        classified_clients AS (
            SELECT c.client_id,
                   CASE
                       WHEN COALESCE(cp.total_portfolio_value, 0) >= :premierMinPortfolioValue
                            AND COALESCE(co.recent_filled_order_count, 0) >= :activeMinOrderCount THEN 'Premier'
                       WHEN COALESCE(co.recent_filled_order_count, 0) >= :activeMinOrderCount THEN 'Active'
                       WHEN COALESCE(cp.total_portfolio_value, 0) <= :dormantMaxPortfolioValue
                            AND COALESCE(co.recent_filled_order_count, 0) <= :dormantMaxOrderCount THEN 'Dormant'
                       ELSE 'Core'
                   END AS segment
            FROM clients c
            LEFT JOIN client_portfolio cp ON cp.client_id = c.client_id
            LEFT JOIN client_orders co ON co.client_id = c.client_id
        ),
        filtered_clients AS (
            SELECT cc.client_id
            FROM classified_clients cc
            WHERE :segmentFilter IS NULL OR cc.segment = :segmentFilter
        ),
        instrument_positions AS (
            SELECT h.instrument_id,
                   SUM(h.quantity * lpq.price) AS current_position_value
            FROM filtered_clients fc
            JOIN accounts a ON a.client_id = fc.client_id
            JOIN holdings h ON h.account_id = a.account_id
            JOIN latest_price_quotes lpq ON lpq.instrument_id = h.instrument_id
            GROUP BY h.instrument_id
        ),
        instrument_activity AS (
            SELECT t.instrument_id,
                   COUNT(*) AS recent_filled_order_count,
                   SUM(CASE WHEN t.side = 'BUY' THEN 1 ELSE 0 END) AS buy_count,
                   SUM(CASE WHEN t.side = 'SELL' THEN 1 ELSE 0 END) AS sell_count,
                   COUNT(DISTINCT t.client_id) AS traded_client_count
            FROM filtered_clients fc
            JOIN reporting.trade_activity t ON t.client_id = fc.client_id
            WHERE t.executed_at >= CURRENT_TIMESTAMP - (:lookbackDays * INTERVAL '1 day')
            GROUP BY t.instrument_id
        )
        SELECT i.ticker AS "ticker",
               i.instrument_name AS "instrumentName",
               i.asset_class AS "assetClass",
               COALESCE(ip.current_position_value, 0) AS "currentPositionValue",
               COALESCE(ia.recent_filled_order_count, 0) AS "recentFilledOrderCount",
               COALESCE(ia.buy_count, 0) AS "buyCount",
               COALESCE(ia.sell_count, 0) AS "sellCount",
               COALESCE(ia.traded_client_count, 0) AS "tradedClientCount"
        FROM instrument_positions ip
        FULL OUTER JOIN instrument_activity ia ON ia.instrument_id = ip.instrument_id
        JOIN instruments i ON i.instrument_id = COALESCE(ip.instrument_id, ia.instrument_id)
        ORDER BY "currentPositionValue" DESC, "recentFilledOrderCount" DESC, i.ticker ASC
        LIMIT :limit
    """, nativeQuery = true)
    List<InstrumentInsightsProjection> findInstrumentInsights(
            @Param("lookbackDays") Integer lookbackDays,
            @Param("segmentFilter") String segmentFilter,
            @Param("dormantMaxPortfolioValue") BigDecimal dormantMaxPortfolioValue,
            @Param("dormantMaxOrderCount") Integer dormantMaxOrderCount,
            @Param("premierMinPortfolioValue") BigDecimal premierMinPortfolioValue,
            @Param("activeMinOrderCount") Integer activeMinOrderCount,
            @Param("limit") Integer limit);
}