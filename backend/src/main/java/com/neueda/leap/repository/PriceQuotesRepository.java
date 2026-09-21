package com.neueda.leap.repository;

import com.neueda.leap.model.PriceQuotes;
import com.neueda.leap.model.dto.PriceHistoryResult;
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
}
