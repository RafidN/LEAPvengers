package com.neueda.leap.repository;

import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.dto.PriceQuoteResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/**
 * Repository for public ticker price quote data
 * Queries the latest_price_quotes materialized view for current market prices
 * No authentication required - this is public data
 * Custom @Query methods return DTOs instead of managed entities
 */
public interface InstrumentsRepository extends JpaRepository<Instruments, Integer> {

    /**
     * Search for price quotes by ticker symbol or instrument name.
     * Uses parameterized query to prevent SQL injection.
     * 
     * @param query The user search term (case-insensitive, partial match)
     * @return List of matching price quotes with current market data
     */
    @Query("""
        SELECT new com.neueda.leap.model.dto.PriceQuoteResult(
            i.ticker,
            i.instrumentName,
            lpq.price,
            i.assetClass,
            lpq.quoteTimestamp
        )
        FROM Instruments i
        JOIN LatestPriceQuotes lpq ON i.instrumentId = lpq.instrumentId
        WHERE UPPER(i.ticker) LIKE UPPER(CONCAT('%', :query, '%'))
            OR UPPER(i.instrumentName) LIKE UPPER(CONCAT('%', :query, '%'))
    """)
    List<PriceQuoteResult> searchInstrumentPrice(@Param("query") String query);
}
