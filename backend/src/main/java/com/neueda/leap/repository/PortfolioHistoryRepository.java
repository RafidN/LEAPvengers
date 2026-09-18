package com.neueda.leap.repository;

import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.dto.PortfolioHistoryResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repository for portfolio holdings as of a specific date
 * User-specific: only returns holdings for authenticated user's accounts
 * Custom @Query methods return DTOs instead of managed entities
 */
public interface PortfolioHistoryRepository extends JpaRepository<Holdings, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            COALESCE(lpq.price, 0.0),
            CAST(h.quantity AS java.lang.Double) * COALESCE(lpq.price, 0.0),
            CAST(:asOfDate AS string)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate <= :asOfDate
        ORDER BY i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioAsOfDate(
        @Param("clientId") Integer clientId,
        @Param("asOfDate") String asOfDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            COALESCE(lpq.price, 0.0),
            CAST(h.quantity AS java.lang.Double) * COALESCE(lpq.price, 0.0),
            CAST(h.asOfDate AS string)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - INTERVAL YEAR
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            COALESCE(lpq.price, 0.0),
            CAST(h.quantity AS java.lang.Double) * COALESCE(lpq.price, 0.0),
            CAST(h.asOfDate AS string)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - INTERVAL MONTH
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            COALESCE(lpq.price, 0.0),
            CAST(h.quantity AS java.lang.Double) * COALESCE(lpq.price, 0.0),
            CAST(h.asOfDate AS string)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - INTERVAL WEEK
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            COALESCE(lpq.price, 0.0),
            CAST(h.quantity AS java.lang.Double) * COALESCE(lpq.price, 0.0),
            CAST(h.asOfDate AS string)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - INTERVAL DAY
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            COALESCE(lpq.price, 0.0),
            CAST(h.quantity AS java.lang.Double) * COALESCE(lpq.price, 0.0),
            CAST(h.asOfDate AS string)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate = CURRENT_DATE
        ORDER BY i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioToday(@Param("clientId") Integer clientId);
}
