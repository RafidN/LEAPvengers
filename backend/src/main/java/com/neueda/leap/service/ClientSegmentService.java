package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.ClientSegmentMetricsProjection;
import com.neueda.leap.model.dto.ClientSegmentQueryRequest;
import com.neueda.leap.model.dto.ClientSegmentResult;
import com.neueda.leap.repository.ClientRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service for querying client segments using portfolio value and recent order activity.
 */
@Service
public class ClientSegmentService {

    private static final int DEFAULT_LOOKBACK_DAYS = 90;
    private static final BigDecimal DEFAULT_DORMANT_MAX_PORTFOLIO_VALUE = new BigDecimal("25000");
    private static final int DEFAULT_DORMANT_MAX_ORDER_COUNT = 1;
    private static final BigDecimal DEFAULT_PREMIER_MIN_PORTFOLIO_VALUE = new BigDecimal("250000");
    private static final int DEFAULT_ACTIVE_MIN_ORDER_COUNT = 12;

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;

    public ClientSegmentService(ClientRepository clientRepository, UserRepository userRepository) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientSegmentResult> getClientSegments(Integer userId, ClientSegmentQueryRequest request)
            throws UserNotFoundException, InvalidInputException {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        SegmentCriteria criteria = resolveCriteria(request);

        return clientRepository.findClientSegmentMetrics(criteria.lookbackDays()).stream()
                .map(row -> toResult(row, criteria))
                .filter(result -> matchesSegmentFilter(result, criteria.segmentFilter()))
                .sorted(clientSegmentComparator())
                .toList();
    }

    private SegmentCriteria resolveCriteria(ClientSegmentQueryRequest request) throws InvalidInputException {
        ClientSegmentQueryRequest effectiveRequest = request == null ? new ClientSegmentQueryRequest() : request;

        int lookbackDays = effectiveRequest.getLookbackDays() == null
                ? DEFAULT_LOOKBACK_DAYS
                : effectiveRequest.getLookbackDays();

        BigDecimal dormantMaxPortfolioValue = effectiveRequest.getDormantMaxPortfolioValue() == null
                ? DEFAULT_DORMANT_MAX_PORTFOLIO_VALUE
                : effectiveRequest.getDormantMaxPortfolioValue();

        int dormantMaxOrderCount = effectiveRequest.getDormantMaxOrderCount() == null
                ? DEFAULT_DORMANT_MAX_ORDER_COUNT
                : effectiveRequest.getDormantMaxOrderCount();

        BigDecimal premierMinPortfolioValue = effectiveRequest.getPremierMinPortfolioValue() == null
                ? DEFAULT_PREMIER_MIN_PORTFOLIO_VALUE
                : effectiveRequest.getPremierMinPortfolioValue();

        int activeMinOrderCount = effectiveRequest.getActiveMinOrderCount() == null
                ? DEFAULT_ACTIVE_MIN_ORDER_COUNT
                : effectiveRequest.getActiveMinOrderCount();

        String segmentFilter = normalizeSegment(effectiveRequest.getSegment());

        validateCriteria(lookbackDays, dormantMaxPortfolioValue, dormantMaxOrderCount,
                premierMinPortfolioValue, activeMinOrderCount, segmentFilter);

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

        if (segmentFilter != null && segmentRank(segmentFilter) == Integer.MAX_VALUE) {
            throw new InvalidInputException("Segment must be one of Dormant, Core, Active, or Premier");
        }
    }

    private ClientSegmentResult toResult(ClientSegmentMetricsProjection row, SegmentCriteria criteria) {
        BigDecimal totalPortfolioValue = row.getTotalPortfolioValue() == null
                ? BigDecimal.ZERO
                : row.getTotalPortfolioValue();

        long recentFilledOrderCount = row.getRecentFilledOrderCount() == null
                ? 0L
                : row.getRecentFilledOrderCount();

        String segment = classifySegment(totalPortfolioValue, recentFilledOrderCount, criteria);

        return new ClientSegmentResult(
                row.getClientId(),
                row.getFirstName(),
                row.getLastName(),
                row.getEmail(),
                totalPortfolioValue,
                recentFilledOrderCount,
                segment
        );
    }

    private String classifySegment(BigDecimal totalPortfolioValue, long recentFilledOrderCount,
                                   SegmentCriteria criteria) {
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

    private boolean matchesSegmentFilter(ClientSegmentResult result, String segmentFilter) {
        return segmentFilter == null || result.getSegment().equals(segmentFilter);
    }

    private Comparator<ClientSegmentResult> clientSegmentComparator() {
        return Comparator
                .comparingInt((ClientSegmentResult result) -> segmentRank(result.getSegment()))
                .thenComparing(ClientSegmentResult::getTotalPortfolioValue, Comparator.reverseOrder())
                .thenComparing(ClientSegmentResult::getRecentFilledOrderCount, Comparator.reverseOrder())
                .thenComparing(ClientSegmentResult::getLastName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ClientSegmentResult::getFirstName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ClientSegmentResult::getClientId);
    }

    private int segmentRank(String segment) {
        return switch (segment) {
            case "Dormant" -> 1;
            case "Core" -> 2;
            case "Active" -> 3;
            case "Premier" -> 4;
            default -> Integer.MAX_VALUE;
        };
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

    private record SegmentCriteria(
            int lookbackDays,
            BigDecimal dormantMaxPortfolioValue,
            int dormantMaxOrderCount,
            BigDecimal premierMinPortfolioValue,
            int activeMinOrderCount,
            String segmentFilter
    ) {
    }
}