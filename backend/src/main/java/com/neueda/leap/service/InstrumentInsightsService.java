package com.neueda.leap.service;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.exception.UserNotFoundException;
import com.neueda.leap.model.dto.InstrumentInsightsProjection;
import com.neueda.leap.model.dto.InstrumentInsightsQueryRequest;
import com.neueda.leap.model.dto.InstrumentInsightsResult;
import com.neueda.leap.repository.InstrumentInsightsRepository;
import com.neueda.leap.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for instrument-level insights analytics.
 */
@Service
public class InstrumentInsightsService {

    static final int DEFAULT_LIMIT = 10;

    private final InstrumentInsightsRepository instrumentInsightsRepository;
    private final UserRepository userRepository;
    private final ClientSegmentRules clientSegmentRules;

    public InstrumentInsightsService(InstrumentInsightsRepository instrumentInsightsRepository,
                                     UserRepository userRepository,
                                     ClientSegmentRules clientSegmentRules) {
        this.instrumentInsightsRepository = instrumentInsightsRepository;
        this.userRepository = userRepository;
        this.clientSegmentRules = clientSegmentRules;
    }

    @Transactional(readOnly = true)
    public List<InstrumentInsightsResult> getInstrumentInsights(Integer userId, InstrumentInsightsQueryRequest request)
            throws UserNotFoundException, InvalidInputException {

        userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        ClientSegmentRules.SegmentCriteria criteria = clientSegmentRules.resolve(request);
        int limit = resolveLimit(request);

        return instrumentInsightsRepository.findInstrumentInsights(
                        criteria.lookbackDays(),
                        criteria.segmentFilter(),
                        criteria.dormantMaxPortfolioValue(),
                        criteria.dormantMaxOrderCount(),
                        criteria.premierMinPortfolioValue(),
                        criteria.activeMinOrderCount(),
                        limit
                ).stream()
                .map(this::toResult)
                .toList();
    }

    private int resolveLimit(InstrumentInsightsQueryRequest request) throws InvalidInputException {
        int limit = request == null || request.getLimit() == null ? DEFAULT_LIMIT : request.getLimit();
        if (limit <= 0) {
            throw new InvalidInputException("Limit must be positive");
        }
        return limit;
    }

    private InstrumentInsightsResult toResult(InstrumentInsightsProjection projection) {
        return new InstrumentInsightsResult(
                projection.getTicker(),
                projection.getInstrumentName(),
                projection.getAssetClass(),
                defaultValue(projection.getCurrentPositionValue()),
                defaultValue(projection.getRecentFilledOrderCount()),
                defaultValue(projection.getBuyCount()),
                defaultValue(projection.getSellCount()),
                defaultValue(projection.getTradedClientCount())
        );
    }

    private BigDecimal defaultValue(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long defaultValue(Long value) {
        return value == null ? 0L : value;
    }
}