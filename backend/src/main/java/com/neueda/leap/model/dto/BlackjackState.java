package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the Risk Lab blackjack page shows: the table, what the player may do next, the coach, session
 * statistics and the reality check against the player's real portfolio. Play money only.
 */
public record BlackjackState(
        boolean seated,
        Table table,
        Coach coach,
        Stats stats,
        RealityCheck realityCheck) {

    /** hands: the player's hands; dealerCards: codes like "10H", null for the face-down hole card. */
    public record Table(
            BigDecimal chips,
            BigDecimal onTable,
            boolean inRound,
            List<HandView> hands,
            Integer currentHand,
            List<String> dealerCards,
            int dealerShowing,
            boolean canDeal,
            boolean canHit,
            boolean canStand,
            boolean canDouble,
            boolean canSplit,
            String message,
            int cardsLeftInShoe,
            BigDecimal minBet,
            BigDecimal maxBet) {
    }

    public record HandView(List<String> cards, int total, boolean soft, BigDecimal bet, boolean doubled,
                           String outcome, BigDecimal payout) {
    }

    /**
     * recommended: basic strategy's play for the hand waiting on a decision (HIT, STAND, DOUBLE or SPLIT).
     * lastTaken / lastRecommended: the previous decision and what basic strategy would have done.
     */
    public record Coach(String recommended, String situation, String lastTaken, String lastRecommended,
                        String lastSituation, boolean lastFollowed) {
    }

    /** expectedLoss = what the house edge takes, on average, from everything wagered so far. */
    public record Stats(int roundsPlayed, BigDecimal wagered, BigDecimal net, BigDecimal netPercent,
                        int decisions, int decisionsByTheBook, BigDecimal houseEdgePercent,
                        BigDecimal expectedLoss) {
    }

    /**
     * The player's real portfolio over the same minutes (cash plus holdings at live prices), and, when they
     * pretend-staked a holding, how many of its shares the chips would buy back at the price they started at.
     */
    public record RealityCheck(LocalDateTime seatedAt, long minutesAtTable, BigDecimal portfolioAtStart,
                               BigDecimal portfolioNow, BigDecimal portfolioChange, BigDecimal portfolioChangePercent,
                               String stakeTicker, BigDecimal stakeQuantity, BigDecimal stakePrice,
                               BigDecimal stakeQuantityNow) {
    }

    public static BlackjackState notSeated() {
        return new BlackjackState(false, null, null, null, null);
    }
}
