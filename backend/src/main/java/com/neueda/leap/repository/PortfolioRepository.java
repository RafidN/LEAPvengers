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
public interface PortfolioRepository extends JpaRepository<Holdings, Integer> {

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            h.quantity,
            lpq.price,
            h.quantity * lpq.price,
            :asOfDate
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate <= :asOfDate
        ORDER BY i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioAsOfDate(
        @Param("clientId") Integer clientId,
        @Param("asOfDate") java.time.LocalDate asOfDate
    );

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            h.quantity,
            lpq.price,
            h.quantity * lpq.price,
            h.asOfDate
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - 1 YEAR
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPastYear(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            h.quantity,
            lpq.price,
            h.quantity * lpq.price,
            h.asOfDate
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - 1 MONTH
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPastMonth(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            h.quantity,
            lpq.price,
            h.quantity * lpq.price,
            h.asOfDate
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - 7 DAY
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPast7Days(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            h.quantity,
            lpq.price,
            h.quantity * lpq.price,
            h.asOfDate
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate >= CURRENT_DATE - 1 DAY
        ORDER BY h.asOfDate DESC, i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioPastDay(@Param("clientId") Integer clientId);

    @Query("""
        SELECT new com.neueda.leap.model.dto.PortfolioHistoryResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            h.quantity,
            lpq.price,
            h.quantity * lpq.price,
            h.asOfDate
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        LEFT JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE a.clientId = :clientId
            AND h.asOfDate = CURRENT_DATE
        ORDER BY i.ticker
    """)
    List<PortfolioHistoryResult> findPortfolioToday(@Param("clientId") Integer clientId);
}
