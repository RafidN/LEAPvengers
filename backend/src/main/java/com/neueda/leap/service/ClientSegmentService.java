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

/**
 * Service for querying client segments using portfolio value and recent order activity.
 */
@Service
public class ClientSegmentService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final ClientSegmentRules clientSegmentRules;

    public ClientSegmentService(ClientRepository clientRepository, UserRepository userRepository,
                                ClientSegmentRules clientSegmentRules) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.clientSegmentRules = clientSegmentRules;
    }

    @Transactional(readOnly = true)
    public List<ClientSegmentResult> getClientSegments(Integer userId, ClientSegmentQueryRequest request)
            throws UserNotFoundException, InvalidInputException {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ClientSegmentRules.SegmentCriteria criteria = clientSegmentRules.resolve(request);

        return clientRepository.findClientSegmentMetrics(criteria.lookbackDays()).stream()
                .map(row -> toResult(row, criteria))
                .filter(result -> matchesSegmentFilter(result, criteria.segmentFilter()))
                .sorted(clientSegmentComparator())
                .toList();
    }

    private ClientSegmentResult toResult(ClientSegmentMetricsProjection row, ClientSegmentRules.SegmentCriteria criteria) {
        BigDecimal totalPortfolioValue = row.getTotalPortfolioValue() == null
                ? BigDecimal.ZERO
                : row.getTotalPortfolioValue();

        long recentFilledOrderCount = row.getRecentFilledOrderCount() == null
                ? 0L
                : row.getRecentFilledOrderCount();

        String segment = clientSegmentRules.classifySegment(totalPortfolioValue, recentFilledOrderCount, criteria);

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
}