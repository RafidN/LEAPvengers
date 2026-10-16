package com.neueda.leap.service;

import com.neueda.leap.model.ReportingLoadRun;
import com.neueda.leap.model.TradeActivity;
import com.neueda.leap.model.dto.FilledOrderRow;
import com.neueda.leap.repository.OrderRepository;
import com.neueda.leap.repository.ReportingLoadRunRepository;
import com.neueda.leap.repository.TradeActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the reporting store (reporting.trade_activity) up to date: each run copies in the orders filled
 * since the last one, in one short batch, so analysts' reports never need to read the live tables.
 *
 * Re-running is harmless: an order already in the store is skipped. The window reaches back OVERLAP before
 * the newest stored fill, so a fill committed a little after a later one was loaded is still picked up.
 * Each trade is tagged with its client's segment at the time it is loaded, so a report by segment shows
 * who the client was when they traded (trades loaded on the store's first run all get today's segment).
 */
@Service
public class ReportingLoadService {
    static final Duration OVERLAP = Duration.ofMinutes(10);
    private static final Duration KEEP_RUNS = Duration.ofDays(7);
    private static final int BATCH = 1_000;
    private static final String UNKNOWN_SEGMENT = "Unclassified";

    private final OrderRepository orderRepository;
    private final TradeActivityRepository tradeActivityRepository;
    private final ReportingLoadRunRepository loadRunRepository;
    private final ClientSegmentService clientSegmentService;
    private final Clock clock;

    @Autowired
    public ReportingLoadService(OrderRepository orderRepository, TradeActivityRepository tradeActivityRepository,
                                ReportingLoadRunRepository loadRunRepository, ClientSegmentService clientSegmentService) {
        // Order times are written in the server's local time, so compare in local time too
        this(orderRepository, tradeActivityRepository, loadRunRepository, clientSegmentService, Clock.systemDefaultZone());
    }

    ReportingLoadService(OrderRepository orderRepository, TradeActivityRepository tradeActivityRepository,
                         ReportingLoadRunRepository loadRunRepository, ClientSegmentService clientSegmentService,
                         Clock clock) {
        this.orderRepository = orderRepository;
        this.tradeActivityRepository = tradeActivityRepository;
        this.loadRunRepository = loadRunRepository;
        this.clientSegmentService = clientSegmentService;
        this.clock = clock;
    }

    /**
     * Copies new fills into the reporting store and returns how many were added. Runs from one scheduler
     * thread; were two loads ever to overlap, the order_id primary key stops a fill being stored twice.
     */
    @Transactional
    public int load() {
        LocalDateTime startedAt = LocalDateTime.now(clock);
        LocalDateTime latest = tradeActivityRepository.findLatestExecutedAt();
        LocalDateTime since = latest == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : latest.minus(OVERLAP);

        List<FilledOrderRow> fills = orderRepository.findFilledSince(since);
        Set<Integer> alreadyLoaded = new HashSet<>();
        for (int i = 0; i < fills.size(); i += BATCH) {
            List<Integer> ids = fills.subList(i, Math.min(i + BATCH, fills.size())).stream()
                    .map(FilledOrderRow::getOrderId).toList();
            alreadyLoaded.addAll(tradeActivityRepository.findLoadedOrderIds(ids));
        }

        List<TradeActivity> newRows = new ArrayList<>();
        for (FilledOrderRow fill : fills) {
            if (!alreadyLoaded.contains(fill.getOrderId())) {
                newRows.add(new TradeActivity(fill.getOrderId(), fill.getExecutedAt(), fill.getClientId(), UNKNOWN_SEGMENT,
                        fill.getInstrumentId(), fill.getTicker(), fill.getAssetClass(), fill.getMarket(), fill.getSide(),
                        fill.getQuantity(), fill.getPrice(), startedAt));
            }
        }
        if (!newRows.isEmpty()) {
            // The segment rules count each client's recent trades from this store, so the new rows go in first;
            // otherwise a client's latest trades (or, on the first load, all of them) would be left out
            tradeActivityRepository.saveAllAndFlush(newRows);
            Map<Integer, String> segments = clientSegmentService.getCurrentSegments();
            for (TradeActivity row : newRows) {
                row.tagSegment(segments.getOrDefault(row.getClientId(), UNKNOWN_SEGMENT));
            }
        }

        LocalDateTime finishedAt = LocalDateTime.now(clock);
        loadRunRepository.save(new ReportingLoadRun(startedAt, finishedAt, newRows.size()));
        loadRunRepository.deleteFinishedBefore(finishedAt.minus(KEEP_RUNS));
        return newRows.size();
    }
}
