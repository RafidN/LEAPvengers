package com.neueda.leap.repository;

import com.neueda.leap.model.LatestPriceQuotes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for latest price quotes (read-only materialized view)
 * Provides access to the most recent price for each instrument
 */
@Repository
public interface LatestPriceQuotesRepository extends JpaRepository<LatestPriceQuotes, Integer> {
    /**
     * Find the latest price quote for an instrument by its ID
     * 
     * @param instrumentId The instrument ID
     * @return Optional containing the latest price quote, empty if not found
     */
    Optional<LatestPriceQuotes> findByInstrumentId(Integer instrumentId);
}
