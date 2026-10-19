package com.neueda.leap.service.blackjack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One of the player's hands: its cards, its bet, and how it ended. */
public final class Hand {
    public enum Outcome { PLAYING, BLACKJACK, WIN, PUSH, LOSE, BUST }

    final List<Card> cards = new ArrayList<>();
    BigDecimal bet;
    boolean doubled;
    /** Came from a split: a two-card 21 then counts as 21, not blackjack. */
    boolean fromSplit;
    /** Split aces get one card each and no more. */
    boolean splitAces;
    boolean done;
    Outcome outcome = Outcome.PLAYING;
    /** Chips returned to the player when the round settled (stake included); 0 for a loss. */
    BigDecimal payout = BigDecimal.ZERO;

    Hand(BigDecimal bet) {
        this.bet = bet;
    }

    /** Best total: aces count 11 unless that would bust the hand. */
    public int total() {
        return totalOf(cards);
    }

    /** True when an ace is counting as 11, so one more card can't bust it. */
    public boolean soft() {
        return isSoft(cards);
    }

    public boolean blackjack() {
        return !fromSplit && cards.size() == 2 && total() == 21;
    }

    public boolean busted() {
        return total() > 21;
    }

    public List<Card> cards() {
        return List.copyOf(cards);
    }

    public BigDecimal bet() {
        return bet;
    }

    public boolean doubled() {
        return doubled;
    }

    public boolean done() {
        return done;
    }

    public Outcome outcome() {
        return outcome;
    }

    public BigDecimal payout() {
        return payout;
    }

    static int totalOf(List<Card> cards) {
        int hard = cards.stream().mapToInt(c -> c.isAce() ? 1 : c.value()).sum();
        boolean hasAce = cards.stream().anyMatch(Card::isAce);
        return hasAce && hard + 10 <= 21 ? hard + 10 : hard;
    }

    static boolean isSoft(List<Card> cards) {
        int hard = cards.stream().mapToInt(c -> c.isAce() ? 1 : c.value()).sum();
        return cards.stream().anyMatch(Card::isAce) && hard + 10 <= 21;
    }
}
