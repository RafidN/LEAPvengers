package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.dto.ActivityReportResponse;
import com.neueda.leap.model.dto.ActivityReportRow;
import com.neueda.leap.repository.ReportingLoadRunRepository;
import com.neueda.leap.repository.TradeActivityRepository;
import com.neueda.leap.repository.TradeActivityRepository.DailyClientActivity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportingServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);   // a Thursday

    private TradeActivityRepository tradeActivityRepository;
    private ReportingService service;

    @BeforeEach
    void setUp() {
        tradeActivityRepository = mock(TradeActivityRepository.class);
        ReportingLoadRunRepository loadRuns = mock(ReportingLoadRunRepository.class);
        when(loadRuns.findTopByOrderByFinishedAtDesc()).thenReturn(Optional.empty());
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service = new ReportingService(tradeActivityRepository, loadRuns, clock);
    }

    private static DailyClientActivity day(LocalDate date, int clientId, long trades, long buys, String notional) {
        return new DailyClientActivity() {
            public LocalDate getTradeDate() { return date; }
            public Integer getClientId() { return clientId; }
            public Long getTrades() { return trades; }
            public Long getBuys() { return buys; }
            public BigDecimal getNotional() { return new BigDecimal(notional); }
        };
    }

    @Test
    void weeksCountEachClientOnceAndKeepQuietWeeks() {
        // Client 1 trades on Monday and Wednesday of the week of Oct 5; client 2 on that Wednesday too
        List<DailyClientActivity> daily = List.of(
                day(LocalDate.of(2026, 10, 5), 1, 2, 2, "100"),
                day(LocalDate.of(2026, 10, 7), 1, 1, 0, "50"),
                day(LocalDate.of(2026, 10, 7), 2, 3, 1, "300"));

        List<ActivityReportRow> weeks = ReportingService.byPeriod(daily, LocalDate.of(2026, 9, 30), TODAY, "week");

        // Weeks of Sep 28, Oct 5 and Oct 12, the first and last with no trading
        assertEquals(List.of("2026-09-28", "2026-10-05", "2026-10-12"), weeks.stream().map(ActivityReportRow::getKey).toList());
        ActivityReportRow busy = weeks.get(1);
        assertEquals("Week of Oct 5, 2026", busy.getLabel());
        assertEquals(6, busy.getTrades());
        assertEquals(3, busy.getBuys());
        assertEquals(3, busy.getSells());
        assertEquals(new BigDecimal("450.00"), busy.getTradedValue());
        assertEquals(2, busy.getActiveClients());
        assertEquals(0, weeks.get(0).getTrades());
    }

    @Test
    void monthsStartOnTheFirst() {
        List<ActivityReportRow> months = ReportingService.byPeriod(
                List.of(day(LocalDate.of(2026, 9, 20), 1, 1, 1, "10")), LocalDate.of(2026, 8, 15), TODAY, "month");

        assertEquals(List.of("August 2026", "September 2026", "October 2026"),
                months.stream().map(ActivityReportRow::getLabel).toList());
        assertEquals(1, months.get(1).getTrades());
    }

    @Test
    void defaultsToTheLast30DaysByDay() {
        when(tradeActivityRepository.findDailyClientActivity(any(), any(), isNull())).thenReturn(List.of(
                day(TODAY, 1, 1, 1, "10"), day(TODAY.minusDays(1), 1, 1, 0, "20")));

        ActivityReportResponse report = service.getActivity(null, null, null, " ");

        assertEquals(TODAY.minusDays(29), report.getFrom());
        assertEquals(TODAY, report.getTo());
        assertEquals("day", report.getGroupBy());
        assertEquals(30, report.getRows().size());
        // The same client on two days is still one active client in the totals
        assertEquals(1, report.getTotals().getActiveClients());
        assertEquals(2, report.getTotals().getTrades());
    }

    @Test
    void groupingIsMatchedWithoutCase() {
        assertEquals("assetClass", service.getActivity(null, null, "ASSETCLASS", null).getGroupBy());
    }

    @Test
    void turnsDownBadRanges() {
        assertThrows(InvalidInputException.class, () -> service.getActivity(TODAY, TODAY.minusDays(1), "day", null));
        assertThrows(InvalidInputException.class, () -> service.getActivity(TODAY.minusYears(4), TODAY, "day", null));
        assertThrows(InvalidInputException.class, () -> service.getActivity(null, null, "hour", null));
    }
}
