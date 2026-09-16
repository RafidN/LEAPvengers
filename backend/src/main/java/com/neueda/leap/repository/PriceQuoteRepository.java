package com.neueda.leap.repository;

import com.neueda.leap.model.dto.PriceQuoteResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for public ticker price quote data
 * Queries the latest_price_quotes materialized view for current market prices
 * No authentication required - this is public data
 */
@Repository
public interface PriceQuoteRepository extends JpaRepository<Object, Object> {

    /**
     * Search for ticker price quotes by ticker symbol
     * Uses parameterized query to prevent SQL injection
     * 
     * @param ticker The ticker symbol to search for (case-insensitive, partial match)
     * @return List of matching price quotes with current market data
     */
    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceQuoteResult(
            i.ticker,
            i.instrumentName,
            lpq.price,
            i.assetClass,
            CAST(lpq.quoteTimestamp AS string)
        )
        FROM Instruments i
        JOIN LatestPriceQuotes lpq ON i.id = lpq.instrumentId
        WHERE UPPER(i.ticker) LIKE UPPER(CONCAT('%', :ticker, '%'))
    """)
    List<PriceQuoteResult> searchTickerPrice(@Param("ticker") String ticker);
}
