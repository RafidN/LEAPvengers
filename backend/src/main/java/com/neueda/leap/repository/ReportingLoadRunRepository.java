package com.neueda.leap.repository;

import com.neueda.leap.model.ReportingLoadRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReportingLoadRunRepository extends JpaRepository<ReportingLoadRun, Integer> {

    /** The most recent load: how up to date the reporting store is. */
    Optional<ReportingLoadRun> findTopByOrderByFinishedAtDesc();

    /** Loads run every minute, so only the recent ones are kept. */
    @Modifying
    @Query("DELETE FROM ReportingLoadRun r WHERE r.finishedAt < :before")
    int deleteFinishedBefore(@Param("before") LocalDateTime before);
}
