package com.neueda.leap.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A trading activity report, read from the reporting store. dataAsOf is when the store was last brought up to date. */
public class ActivityReportResponse {
    private LocalDate from;
    private LocalDate to;
    private String groupBy;
    private String segment;
    private LocalDateTime dataAsOf;
    private List<ActivityReportRow> rows;
    private ActivityReportRow totals;

    public ActivityReportResponse() {
    }

    public ActivityReportResponse(LocalDate from, LocalDate to, String groupBy, String segment, LocalDateTime dataAsOf,
                                  List<ActivityReportRow> rows, ActivityReportRow totals) {
        this.from = from;
        this.to = to;
        this.groupBy = groupBy;
        this.segment = segment;
        this.dataAsOf = dataAsOf;
        this.rows = rows;
        this.totals = totals;
    }

    public LocalDate getFrom() {
        return from;
    }

    public LocalDate getTo() {
        return to;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public String getSegment() {
        return segment;
    }

    public LocalDateTime getDataAsOf() {
        return dataAsOf;
    }

    public List<ActivityReportRow> getRows() {
        return rows;
    }

    public ActivityReportRow getTotals() {
        return totals;
    }
}
