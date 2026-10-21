package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.ReportingLoadRun;
import com.neueda.leap.model.dto.ActivityReportResponse;
import com.neueda.leap.model.dto.ActivityReportRow;
import com.neueda.leap.model.dto.OrderHealthDailyRate;
import com.neueda.leap.model.dto.OrderHealthReportResponse;
import com.neueda.leap.model.dto.OrderHealthStatusCount;
import com.neueda.leap.model.dto.OrderStatusCountByDateProjection;
import com.neueda.leap.model.dto.OrderStatusCountProjection;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.ReportingLoadRunRepository;
import com.neueda.leap.repository.TradeActivityRepository;
import com.neueda.leap.repository.TradeActivityRepository.DailyClientActivity;
import com.neueda.leap.repository.TradeActivityRepository.GroupActivity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Trading activity over time, by period, instrument, asset class or client segment (BR-16).
 * Reads only the reporting store, so no report competes with live trading. Clients are only ever counted,
 * never named.
 */
@Service
public class ReportingService {
    static final int DEFAULT_DAYS = 30;
    static final int DEFAULT_ORDER_HEALTH_DAYS = 90;
    static final int MAX_DAYS = 3 * 366;
    static final List<String> GROUPINGS = List.of("day", "week", "month", "instrument", "assetClass", "segment");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final TradeActivityRepository tradeActivityRepository;
    private final OrderRepository orderRepository;
    private final ReportingLoadRunRepository loadRunRepository;
    private final Clock clock;

    @Autowired
    public ReportingService(TradeActivityRepository tradeActivityRepository, OrderRepository orderRepository,
                            ReportingLoadRunRepository loadRunRepository) {
        this(tradeActivityRepository, orderRepository, loadRunRepository, Clock.systemDefaultZone());
    }

    ReportingService(TradeActivityRepository tradeActivityRepository, OrderRepository orderRepository,
                     ReportingLoadRunRepository loadRunRepository,
                     Clock clock) {
        this.tradeActivityRepository = tradeActivityRepository;
        this.orderRepository = orderRepository;
        this.loadRunRepository = loadRunRepository;
        this.clock = clock;
    }

    /**
     * from and to are inclusive trade dates (default: the last 30 days); groupBy is one of GROUPINGS
     * (default day); segment, if given, limits the report to that client segment.
     */
    @Transactional(readOnly = true)
    public ActivityReportResponse getActivity(LocalDate from, LocalDate to, String groupBy, String segment) {
        DateRange range = resolveDateRange(from, to, DEFAULT_DAYS);
        LocalDate start = range.start();
        LocalDate end = range.end();
        String grouping = resolveGrouping(groupBy);
        String segmentFilter = segment == null || segment.isBlank() ? null : segment.trim();

        // Every grouping needs the day-by-client figures for its totals (distinct clients don't add up across groups)
        List<DailyClientActivity> daily = tradeActivityRepository.findDailyClientActivity(start, end, segmentFilter);
        List<ActivityReportRow> rows = switch (grouping) {
            case "day", "week", "month" -> byPeriod(daily, start, end, grouping);
            case "instrument" -> byGroup(tradeActivityRepository.findActivityByInstrument(start, end, segmentFilter));
            case "assetClass" -> byGroup(tradeActivityRepository.findActivityByAssetClass(start, end, segmentFilter));
            default -> byGroup(tradeActivityRepository.findActivityBySegment(start, end, segmentFilter));
        };

        return new ActivityReportResponse(start, end, grouping, segmentFilter,
                loadRunRepository.findTopByOrderByFinishedAtDesc().map(ReportingLoadRun::getFinishedAt).orElse(null),
                rows, totals(daily));
    }

    /**
     * Order-health analytics by status over a date range.
     * Success is FILLED / total orders; failure is (REJECTED + FAILED) / total orders.
     */
    @Transactional(readOnly = true)
    public OrderHealthReportResponse getOrderHealth(LocalDate from, LocalDate to) {
        DateRange range = resolveDateRange(from, to, DEFAULT_ORDER_HEALTH_DAYS);
        List<OrderStatusCountProjection> counts = orderRepository.countByStatusBetween(range.start(), range.end());
        List<OrderStatusCountByDateProjection> countsByDate = orderRepository.countByStatusByDateBetween(range.start(), range.end());

        long submitted = countFor(counts, "SUBMITTED");
        long accepted = countFor(counts, "ACCEPTED");
        long filled = countFor(counts, "FILLED");
        long rejected = countFor(counts, "REJECTED");
        long failed = countFor(counts, "FAILED");
        long total = submitted + accepted + filled + rejected + failed;

        return new OrderHealthReportResponse(
                range.start(),
                range.end(),
                total,
                submitted,
                accepted,
                filled,
                rejected,
                failed,
                ratio(filled, total),
                ratio(rejected + failed, total),
                List.of(
                        new OrderHealthStatusCount("SUBMITTED", submitted),
                        new OrderHealthStatusCount("ACCEPTED", accepted),
                        new OrderHealthStatusCount("FILLED", filled),
                        new OrderHealthStatusCount("REJECTED", rejected),
                        new OrderHealthStatusCount("FAILED", failed)
                ),
                dailyRates(range.start(), range.end(), countsByDate)
        );
    }

            private List<OrderHealthDailyRate> dailyRates(LocalDate start, LocalDate end,
                                  List<OrderStatusCountByDateProjection> countsByDate) {
            Map<LocalDate, Map<String, Long>> byDay = new HashMap<>();
            for (OrderStatusCountByDateProjection row : countsByDate) {
                byDay.computeIfAbsent(row.getOrderDate(), ignored -> new HashMap<>())
                    .put(row.getOrderStatus(), row.getCount() == null ? 0L : row.getCount());
            }

            List<OrderHealthDailyRate> daily = new ArrayList<>();
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                Map<String, Long> statuses = byDay.getOrDefault(day, Map.of());
                long submitted = statuses.getOrDefault("SUBMITTED", 0L);
                long accepted = statuses.getOrDefault("ACCEPTED", 0L);
                long filled = statuses.getOrDefault("FILLED", 0L);
                long rejected = statuses.getOrDefault("REJECTED", 0L);
                long failed = statuses.getOrDefault("FAILED", 0L);
                long total = submitted + accepted + filled + rejected + failed;
                daily.add(new OrderHealthDailyRate(
                    day,
                    total,
                    filled,
                    rejected,
                    failed,
                    ratio(filled, total),
                    ratio(rejected + failed, total)
                ));
            }
            return daily;
            }

    private DateRange resolveDateRange(LocalDate from, LocalDate to, int defaultDays) {
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(defaultDays - 1L);
        if (start.isAfter(end)) {
            throw new InvalidInputException("The start date must be on or before the end date");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new InvalidInputException("Reports can cover at most " + MAX_DAYS + " days");
        }
        return new DateRange(start, end);
    }

    private long countFor(List<OrderStatusCountProjection> counts, String status) {
        return counts.stream()
                .filter(c -> status.equals(c.getOrderStatus()))
                .findFirst()
                .map(c -> c.getCount() == null ? 0L : c.getCount())
                .orElse(0L);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private static String resolveGrouping(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            return "day";
        }
        return GROUPINGS.stream().filter(g -> g.equalsIgnoreCase(groupBy.trim())).findFirst()
                .orElseThrow(() -> new InvalidInputException(
                        "Unsupported groupBy: " + groupBy + " (use " + String.join(", ", GROUPINGS) + ")"));
    }

    /** One row per day, week (starting Monday) or month in the range, including periods with no trading. */
    static List<ActivityReportRow> byPeriod(List<DailyClientActivity> daily, LocalDate from, LocalDate to, String period) {
        Map<LocalDate, Bucket> buckets = new LinkedHashMap<>();
        for (LocalDate d = periodStart(from, period); !d.isAfter(to); d = nextPeriod(d, period)) {
            buckets.put(d, new Bucket());
        }
        for (DailyClientActivity row : daily) {
            buckets.get(periodStart(row.getTradeDate(), period)).add(row);
        }
        List<ActivityReportRow> rows = new ArrayList<>();
        buckets.forEach((startOfPeriod, b) -> rows.add(b.toRow(startOfPeriod.toString(), periodLabel(startOfPeriod, period))));
        return rows;
    }

    private static List<ActivityReportRow> byGroup(List<GroupActivity> groups) {
        return groups.stream()
                .map(g -> new ActivityReportRow(g.getGroupKey(), g.getGroupKey(), g.getTrades(), g.getBuys(),
                        money(g.getNotional()), g.getActiveClients()))
                .sorted(Comparator.comparing(ActivityReportRow::getTradedValue).reversed()
                        .thenComparing(ActivityReportRow::getKey))
                .toList();
    }

    private static ActivityReportRow totals(List<DailyClientActivity> daily) {
        Bucket all = new Bucket();
        daily.forEach(all::add);
        return all.toRow("total", "Total");
    }

    private static LocalDate periodStart(LocalDate date, String period) {
        return switch (period) {
            case "week" -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month" -> date.withDayOfMonth(1);
            default -> date;
        };
    }

    private static LocalDate nextPeriod(LocalDate start, String period) {
        return switch (period) {
            case "week" -> start.plusWeeks(1);
            case "month" -> start.plusMonths(1);
            default -> start.plusDays(1);
        };
    }

    private static String periodLabel(LocalDate start, String period) {
        return switch (period) {
            case "week" -> "Week of " + start.format(DAY);
            case "month" -> start.format(MONTH);
            default -> start.format(DAY);
        };
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    /** Running totals for one period, counting each client once however many days they traded. */
    private static final class Bucket {
        private long trades;
        private long buys;
        private BigDecimal notional = BigDecimal.ZERO;
        private final Set<Integer> clients = new HashSet<>();

        void add(DailyClientActivity row) {
            trades += row.getTrades();
            buys += row.getBuys();
            notional = notional.add(row.getNotional() == null ? BigDecimal.ZERO : row.getNotional());
            clients.add(row.getClientId());
        }

        ActivityReportRow toRow(String key, String label) {
            return new ActivityReportRow(key, label, trades, buys, money(notional), clients.size());
        }
    }
}
