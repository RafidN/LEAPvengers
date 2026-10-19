package com.neueda.leap.repository;

import com.neueda.leap.model.Holdings;
import com.neueda.leap.model.dto.TickerSearchResult;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Repository with parameterized query for ticker search
 * All parameters are bound safely - prevents SQL injection
 */
public interface HoldingsRepository extends JpaRepository<Holdings, Integer> {

    /**
     * Search holdings by ticker symbol or instrument name with client isolation.
     * 
     * @param clientId From JWT token - ensures user only sees their data
     * @param query User input - parameterized to prevent SQL injection
     * @return Matching holdings
     */

    // Parameterized query to safely search holdings by ticker symbol or instrument name for a specific client.
    @Query("""
        SELECT new com.neueda.leap.model.dto.TickerSearchResult(
            h.holdingId,
            a.accountId,
            i.ticker,
            i.instrumentName,
            CAST(h.quantity AS java.lang.Long),
            CAST(0.0 AS java.lang.Double)
        )
        FROM Holdings h
        JOIN h.instrument i
        JOIN h.account a
        WHERE a.clientId = :clientId
            AND (
                UPPER(i.ticker) LIKE UPPER(CONCAT('%', :query, '%'))
                OR UPPER(i.instrumentName) LIKE UPPER(CONCAT('%', :query, '%'))
            )
    """)
    List<TickerSearchResult> searchByInstrumentQuery(
        @Param("clientId") Integer clientId,
        @Param("query") String query
    );

    /** The position one account holds in one instrument, if any. */
    Optional<Holdings> findByAccountIdAndInstrumentId(Integer accountId, Integer instrumentId);

    /** Every holding in the given accounts, e.g. all of one trader's positions. */
    List<Holdings> findByAccountIdIn(Collection<Integer> accountIds);
}
