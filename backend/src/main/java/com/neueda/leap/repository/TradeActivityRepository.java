package com.neueda.leap.repository;

import com.neueda.leap.model.TradeActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Reads and fills the reporting store (reporting.trade_activity). Every report query is here, so it's
 * easy to see that analysis never touches the live trading tables. segment is optional: null means all.
 */
public interface TradeActivityRepository extends JpaRepository<TradeActivity, Integer> {

    /** One trader's trading on one day: rolled up into days, weeks or months by ReportingService. */
    interface DailyClientActivity {
        LocalDate getTradeDate();
        Integer getClientId();
        Long getTrades();
        Long getBuys();
        BigDecimal getNotional();
    }

    /** Trading for one instrument, asset class or segment over the whole range. */
    interface GroupActivity {
        String getGroupKey();
        Long getTrades();
        Long getBuys();
        BigDecimal getNotional();
        Long getActiveClients();
    }

    @Query("SELECT MAX(t.executedAt) FROM TradeActivity t")
    LocalDateTime findLatestExecutedAt();

    @Query("SELECT t.orderId FROM TradeActivity t WHERE t.orderId IN :orderIds")
    List<Integer> findLoadedOrderIds(@Param("orderIds") Collection<Integer> orderIds);

    @Query("""
        SELECT t.tradeDate AS tradeDate, t.clientId AS clientId, COUNT(t) AS trades,
               SUM(CASE WHEN t.side = 'BUY' THEN 1 ELSE 0 END) AS buys, SUM(t.notional) AS notional
        FROM TradeActivity t
        WHERE t.tradeDate BETWEEN :from AND :to AND (:segment IS NULL OR t.clientSegment = :segment)
        GROUP BY t.tradeDate, t.clientId
        """)
    List<DailyClientActivity> findDailyClientActivity(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                      @Param("segment") String segment);

    @Query("""
        SELECT t.ticker AS groupKey, COUNT(t) AS trades, SUM(CASE WHEN t.side = 'BUY' THEN 1 ELSE 0 END) AS buys,
               SUM(t.notional) AS notional, COUNT(DISTINCT t.clientId) AS activeClients
        FROM TradeActivity t
        WHERE t.tradeDate BETWEEN :from AND :to AND (:segment IS NULL OR t.clientSegment = :segment)
        GROUP BY t.ticker
        """)
    List<GroupActivity> findActivityByInstrument(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                 @Param("segment") String segment);

    @Query("""
        SELECT t.assetClass AS groupKey, COUNT(t) AS trades, SUM(CASE WHEN t.side = 'BUY' THEN 1 ELSE 0 END) AS buys,
               SUM(t.notional) AS notional, COUNT(DISTINCT t.clientId) AS activeClients
        FROM TradeActivity t
        WHERE t.tradeDate BETWEEN :from AND :to AND (:segment IS NULL OR t.clientSegment = :segment)
        GROUP BY t.assetClass
        """)
    List<GroupActivity> findActivityByAssetClass(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                 @Param("segment") String segment);

    @Query("""
        SELECT t.clientSegment AS groupKey, COUNT(t) AS trades, SUM(CASE WHEN t.side = 'BUY' THEN 1 ELSE 0 END) AS buys,
               SUM(t.notional) AS notional, COUNT(DISTINCT t.clientId) AS activeClients
        FROM TradeActivity t
        WHERE t.tradeDate BETWEEN :from AND :to AND (:segment IS NULL OR t.clientSegment = :segment)
        GROUP BY t.clientSegment
        """)
    List<GroupActivity> findActivityBySegment(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                              @Param("segment") String segment);
}
