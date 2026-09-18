package com.neueda.leap.repository;

import com.neueda.leap.model.dto.TickerSearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository with parameterized query for ticker search
 * All parameters are bound safely - prevents SQL injection
 */
@Repository
public interface TickerRepository extends JpaRepository<com.neueda.leap.model.Holdings, Integer> {

    /**
     * Search holdings by ticker with client isolation
     * 
     * @param clientId From JWT token - ensures user only sees their data
     * @param ticker User input - parameterized to prevent SQL injection
     * @return Matching holdings
     */

    // Parameterized query to safely search holdings by ticker for a specific client. Written in JPQL to leverage JPA's query capabilities and prevent SQL injection.
    @Query("""
        SELECT new com.neueda.leap.model.dto.TickerSearchResult(
            h.holdingId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            CAST(0.0 AS java.lang.Double)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        WHERE a.clientId = :clientId
            AND UPPER(i.ticker) LIKE UPPER(CONCAT('%', :ticker, '%'))
    """)
    List<TickerSearchResult> searchByTicker(
        @Param("clientId") Integer clientId,
        @Param("ticker") String ticker
    );
}
