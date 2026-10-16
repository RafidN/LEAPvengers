package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** One load of the reporting store (reporting.load_runs), so reports can say how fresh they are. */
@Entity
@Table(schema = "reporting", name = "load_runs")
public class ReportingLoadRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Integer runId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    @Column(name = "rows_loaded", nullable = false)
    private Integer rowsLoaded;

    public ReportingLoadRun() {
    }

    public ReportingLoadRun(LocalDateTime startedAt, LocalDateTime finishedAt, Integer rowsLoaded) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.rowsLoaded = rowsLoaded;
    }

    public Integer getRunId() {
        return runId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Integer getRowsLoaded() {
        return rowsLoaded;
    }
}
