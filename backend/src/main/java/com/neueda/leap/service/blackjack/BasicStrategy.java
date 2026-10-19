package com.neueda.leap.service.blackjack;

import java.util.List;

/**
 * The mathematically best play for every hand, for this table's rules: six decks, dealer stands on soft 17,
 * double on any two cards (after a split too), no surrender. Played perfectly it cuts the house edge to
 * about 0.4%; it can't remove it.
 */
public final class BasicStrategy {

    public enum Action { HIT, STAND, DOUBLE, SPLIT }

    /** The best play and why, e.g. DOUBLE, "hard 11 against a 6". */
    public record Advice(Action action, String situation) {
    }

    private BasicStrategy() {
    }

    public static Advice advise(List<Card> hand, Card dealerUp, boolean canDouble, boolean canSplit) {
        int d = dealerUp.value();
        String against = " against a " + (dealerUp.isAce() ? "ace" : dealerUp.value() == 10 ? "10" : dealerUp.rank());

        if (canSplit && hand.size() == 2 && hand.get(0).value() == hand.get(1).value()) {
            Action pair = pair(hand.get(0).value(), d);
            if (pair != null) {
                String name = hand.get(0).isAce() ? "aces" : hand.get(0).value() == 10 ? "tens" : hand.get(0).value() + "s";
                return new Advice(pair, "a pair of " + name + against);
            }
        }

        int total = Hand.totalOf(hand);
        if (Hand.isSoft(hand)) {
            return new Advice(soft(total, d, canDouble), "soft " + total + against);
        }
        return new Advice(hard(total, d, canDouble), "hard " + total + against);
    }

    /** null = don't split; play the pair as an ordinary total. */
    private static Action pair(int value, int d) {
        return switch (value) {
            case 11, 8 -> Action.SPLIT;
            case 9 -> (d <= 6 || d == 8 || d == 9) ? Action.SPLIT : null;
            case 7 -> d <= 7 ? Action.SPLIT : null;
            case 6 -> d <= 6 ? Action.SPLIT : null;
            case 4 -> (d == 5 || d == 6) ? Action.SPLIT : null;
            case 3, 2 -> d <= 7 ? Action.SPLIT : null;
            default -> null; // tens and fives are never split
        };
    }

    private static Action soft(int total, int d, boolean canDouble) {
        if (total >= 19) {
            return Action.STAND;
        }
        if (total == 18) {
            if (d >= 3 && d <= 6) {
                return canDouble ? Action.DOUBLE : Action.STAND;
            }
            return (d == 2 || d == 7 || d == 8) ? Action.STAND : Action.HIT;
        }
        boolean doubleIt = switch (total) {
            case 17 -> d >= 3 && d <= 6;
            case 16, 15 -> d >= 4 && d <= 6;
            case 14, 13 -> d >= 5 && d <= 6;
            default -> false;
        };
        return doubleIt && canDouble ? Action.DOUBLE : Action.HIT;
    }

    private static Action hard(int total, int d, boolean canDouble) {
        if (total >= 17) {
            return Action.STAND;
        }
        if (total >= 13) {
            return d <= 6 ? Action.STAND : Action.HIT;
        }
        if (total == 12) {
            return (d >= 4 && d <= 6) ? Action.STAND : Action.HIT;
        }
        boolean doubleIt = switch (total) {
            case 11 -> d <= 10;
            case 10 -> d <= 9;
            case 9 -> d >= 3 && d <= 6;
            default -> false;
        };
        return doubleIt && canDouble ? Action.DOUBLE : Action.HIT;
    }
}
