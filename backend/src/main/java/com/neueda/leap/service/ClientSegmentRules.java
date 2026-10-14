package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.model.dto.ClientSegmentQueryRequest;
import com.neueda.leap.model.dto.InstrumentInsightsQueryRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Shared defaults and classification rules for client segment-based analytics.
 */
@Component
public class ClientSegmentRules {

    public static final int DEFAULT_LOOKBACK_DAYS = 90;
    public static final BigDecimal DEFAULT_DORMANT_MAX_PORTFOLIO_VALUE = new BigDecimal("25000");
    public static final int DEFAULT_DORMANT_MAX_ORDER_COUNT = 1;
    public static final BigDecimal DEFAULT_PREMIER_MIN_PORTFOLIO_VALUE = new BigDecimal("250000");
    public static final int DEFAULT_ACTIVE_MIN_ORDER_COUNT = 12;

    public SegmentCriteria resolve(ClientSegmentQueryRequest request) throws InvalidInputException {
        ClientSegmentQueryRequest effectiveRequest = request == null ? new ClientSegmentQueryRequest() : request;
        return resolve(
                effectiveRequest.getLookbackDays(),
                effectiveRequest.getSegment(),
                effectiveRequest.getDormantMaxPortfolioValue(),
                effectiveRequest.getDormantMaxOrderCount(),
                effectiveRequest.getPremierMinPortfolioValue(),
                effectiveRequest.getActiveMinOrderCount()
        );
    }

    public SegmentCriteria resolve(InstrumentInsightsQueryRequest request) throws InvalidInputException {
        InstrumentInsightsQueryRequest effectiveRequest = request == null ? new InstrumentInsightsQueryRequest() : request;
        return resolve(
                effectiveRequest.getLookbackDays(),
                effectiveRequest.getSegment(),
                null,
                null,
                null,
                null
        );
    }

    public String classifySegment(BigDecimal totalPortfolioValue, long recentFilledOrderCount, SegmentCriteria criteria) {
        if (totalPortfolioValue.compareTo(criteria.premierMinPortfolioValue()) >= 0
                && recentFilledOrderCount >= criteria.activeMinOrderCount()) {
            return "Premier";
        }

        if (recentFilledOrderCount >= criteria.activeMinOrderCount()) {
            return "Active";
        }

        if (totalPortfolioValue.compareTo(criteria.dormantMaxPortfolioValue()) <= 0
                && recentFilledOrderCount <= criteria.dormantMaxOrderCount()) {
            return "Dormant";
        }

        return "Core";
    }

    private SegmentCriteria resolve(Integer lookbackDaysValue, String segmentValue,
                                    BigDecimal dormantMaxPortfolioValueValue, Integer dormantMaxOrderCountValue,
                                    BigDecimal premierMinPortfolioValueValue, Integer activeMinOrderCountValue)
            throws InvalidInputException {
        int lookbackDays = lookbackDaysValue == null ? DEFAULT_LOOKBACK_DAYS : lookbackDaysValue;
        BigDecimal dormantMaxPortfolioValue = dormantMaxPortfolioValueValue == null
                ? DEFAULT_DORMANT_MAX_PORTFOLIO_VALUE : dormantMaxPortfolioValueValue;
        int dormantMaxOrderCount = dormantMaxOrderCountValue == null
                ? DEFAULT_DORMANT_MAX_ORDER_COUNT : dormantMaxOrderCountValue;
        BigDecimal premierMinPortfolioValue = premierMinPortfolioValueValue == null
                ? DEFAULT_PREMIER_MIN_PORTFOLIO_VALUE : premierMinPortfolioValueValue;
        int activeMinOrderCount = activeMinOrderCountValue == null
                ? DEFAULT_ACTIVE_MIN_ORDER_COUNT : activeMinOrderCountValue;
        String segmentFilter = normalizeSegment(segmentValue);

        validateCriteria(
                lookbackDays,
                dormantMaxPortfolioValue,
                dormantMaxOrderCount,
                premierMinPortfolioValue,
                activeMinOrderCount,
                segmentFilter
        );

        return new SegmentCriteria(
                lookbackDays,
                dormantMaxPortfolioValue,
                dormantMaxOrderCount,
                premierMinPortfolioValue,
                activeMinOrderCount,
                segmentFilter
        );
    }

    private void validateCriteria(int lookbackDays, BigDecimal dormantMaxPortfolioValue, int dormantMaxOrderCount,
                                  BigDecimal premierMinPortfolioValue, int activeMinOrderCount,
                                  String segmentFilter) throws InvalidInputException {
        if (lookbackDays <= 0) {
            throw new InvalidInputException("Lookback days must be positive");
        }

        if (dormantMaxPortfolioValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Dormant max portfolio value cannot be negative");
        }

        if (premierMinPortfolioValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidInputException("Premier min portfolio value cannot be negative");
        }

        if (dormantMaxOrderCount < 0 || activeMinOrderCount < 0) {
            throw new InvalidInputException("Order count thresholds cannot be negative");
        }

        if (premierMinPortfolioValue.compareTo(dormantMaxPortfolioValue) <= 0) {
            throw new InvalidInputException("Premier minimum portfolio value must exceed dormant maximum portfolio value");
        }

        if (segmentFilter != null && !isRecognizedSegment(segmentFilter)) {
            throw new InvalidInputException("Segment must be one of Dormant, Core, Active, or Premier");
        }
    }

    private String normalizeSegment(String segment) {
        if (segment == null || segment.trim().isEmpty()) {
            return null;
        }

        String normalized = segment.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "dormant" -> "Dormant";
            case "core" -> "Core";
            case "active" -> "Active";
            case "premier" -> "Premier";
            default -> segment.trim();
        };
    }

    private boolean isRecognizedSegment(String segment) {
        return switch (segment) {
            case "Dormant", "Core", "Active", "Premier" -> true;
            default -> false;
        };
    }

    public record SegmentCriteria(
            int lookbackDays,
            BigDecimal dormantMaxPortfolioValue,
            int dormantMaxOrderCount,
            BigDecimal premierMinPortfolioValue,
            int activeMinOrderCount,
            String segmentFilter
    ) {
    }
}