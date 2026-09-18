package com.neueda.leap.repository;

import com.neueda.leap.model.PriceQuotes;
import com.neueda.leap.model.dto.PriceHistoryResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for historical price quotes by time period
 * Public data: no authentication required - anyone can view historical prices
 * Custom @Query methods return DTOs instead of managed entities
 */
@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceQuotes, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            CAST(pq.quoteTimestamp AS string)
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND CAST(pq.quoteTimestamp AS date) BETWEEN :startDate AND :endDate
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesByDateRange(
        @Param("ticker") String ticker,
        @Param("startDate") String startDate,
        @Param("endDate") String endDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            CAST(pq.quoteTimestamp AS string)
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND CAST(pq.quoteTimestamp AS date) >= CURRENT_DATE - INTERVAL YEAR
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPastYear(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            CAST(pq.quoteTimestamp AS string)
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND CAST(pq.quoteTimestamp AS date) >= CURRENT_DATE - INTERVAL MONTH
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPastMonth(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            CAST(pq.quoteTimestamp AS string)
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND CAST(pq.quoteTimestamp AS date) >= CURRENT_DATE - INTERVAL WEEK
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPast7Days(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            CAST(pq.quoteTimestamp AS string)
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND CAST(pq.quoteTimestamp AS date) >= CURRENT_DATE - INTERVAL DAY
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesPastDay(@Param("ticker") String ticker);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceHistoryResult(
            i.ticker,
            i.instrumentName,
            pq.price,
            pq.volume,
            CAST(pq.quoteTimestamp AS string)
        )
        FROM PriceQuotes pq
        JOIN pq.instrument i
        WHERE UPPER(i.ticker) = UPPER(:ticker)
            AND CAST(pq.quoteTimestamp AS date) = CURRENT_DATE
        ORDER BY pq.quoteTimestamp DESC
    """)
    List<PriceHistoryResult> findPricesToday(@Param("ticker") String ticker);
}
