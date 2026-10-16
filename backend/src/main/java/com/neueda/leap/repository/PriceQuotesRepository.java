package com.neueda.leap.repository;

import com.neueda.leap.model.PriceQuotes;
import com.neueda.leap.model.dto.PriceHistoryResult;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.LocalDateTime;

/**
 * Repository for historical price quotes by time period
 * Public data: no authentication required - anyone can view historical prices
 * Custom @Query methods return DTOs instead of managed entities
 */
public interface PriceQuotesRepository extends JpaRepository<PriceQuotes, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findAllPriceHistory(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND pq.quoteTimestamp >= :startTimestamp
            AND pq.quoteTimestamp < :endTimestamp
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesByDateRange(
        @Param("ticker") String ticker,
        @Param("startTimestamp") LocalDateTime startTimestamp,
        @Param("endTimestamp") LocalDateTime endTimestamp
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND pq.quoteTimestamp >= CURRENT_TIMESTAMP - 1 YEAR
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPastYear(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND pq.quoteTimestamp >= CURRENT_TIMESTAMP - 1 MONTH
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPastMonth(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND pq.quoteTimestamp >= CURRENT_TIMESTAMP - 7 DAY
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPast7Days(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND pq.quoteTimestamp >= CURRENT_TIMESTAMP - 1 DAY
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPastDay(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            pq.quoteTimestamp
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND pq.quoteTimestamp >= CURRENT_DATE
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesToday(@Param("ticker") String ticker);

    /**
     * For each instrument, the last quote in each interval of stepSeconds after from, up to to, oldest first.
     * Interval k runs from from + (k-1) * step (exclusive) to from + k * step (inclusive), so its last quote
     * is the price at the point from + k * step. This keeps a chart to a few rows per point however many
     * quotes the live generator has written. Postgres only (DISTINCT ON).
     */
    // The interval number is worked out once in the subquery: written out in both DISTINCT ON and ORDER BY,
    // each copy gets its own JDBC parameters and Postgres can't tell the two expressions match
    @Query(value = """
        SELECT DISTINCT ON (b.instrument_id, b.interval_number)
               b.price_quote_id, b.instrument_id, b.price, b.volume, b.quote_timestamp, b.fetched_at, b.source
        FROM (
            SELECT q.*, CEIL(EXTRACT(EPOCH FROM (q.quote_timestamp - CAST(:from AS timestamp))) / :stepSeconds)
                   AS interval_number
            FROM price_quotes q
            WHERE q.instrument_id IN (:instrumentIds)
                AND q.quote_timestamp > :from
                AND q.quote_timestamp <= :to
        ) b
        ORDER BY b.instrument_id, b.interval_number, b.quote_timestamp DESC
        """, nativeQuery = true)
    List<PriceQuotes> findLastQuotePerInterval(@Param("instrumentIds") Collection<Integer> instrumentIds,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("stepSeconds") double stepSeconds);

    /** The last quote for an instrument at or before a moment. */
    Optional<PriceQuotes> findFirstByInstrumentIdAndQuoteTimestampLessThanEqualOrderByQuoteTimestampDesc(
            Integer instrumentId, LocalDateTime at);
}
