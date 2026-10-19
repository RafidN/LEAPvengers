package com.neueda.leap.service.blackjack;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.service.blackjack.BasicStrategy.Action;
import com.neueda.leap.service.blackjack.BasicStrategy.Advice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One player's seat at a blackjack table, with house rules as printed on the felt:
 * <ul>
 *   <li>six decks, reshuffled at the cut card; bets of 5 to 500 chips</li>
 *   <li>blackjack pays 3 to 2; the dealer checks for blackjack under an ace or a 10 before anyone plays</li>
 *   <li>the dealer draws to 17 and stands on every 17, soft ones included</li>
 *   <li>double on any first two cards, after a split too; split pairs up to four hands; split aces get one card each</li>
 *   <li>a 21 made after a split pays 1 to 1, not 3 to 2; no insurance, no surrender</li>
 * </ul>
 * It also keeps score of the player against basic strategy, the best play for every hand.
 * Play money only: nothing here touches an account.
 */
public final class BlackjackTable {
    public static final BigDecimal MIN_BET = new BigDecimal("5");
    public static final BigDecimal MAX_BET = new BigDecimal("500");
    public static final int MAX_HANDS = 4;
    /** The house edge for these rules with perfect basic strategy, as a share of each chip bet. */
    public static final BigDecimal HOUSE_EDGE = new BigDecimal("0.004");

    private final Supplier<Shoe> newShoe;
    private Shoe shoe;
    private final BigDecimal startingChips;
    private BigDecimal chips;

    private final List<Hand> hands = new ArrayList<>();
    private final List<Card> dealer = new ArrayList<>();
    private int current;
    private boolean inRound;
    private boolean holeRevealed;
    private String message = "Place a bet to deal the first hand.";

    private int roundsPlayed;
    private BigDecimal wagered = BigDecimal.ZERO;
    private int decisions;
    private int decisionsByTheBook;
    private Feedback lastFeedback;

    /** What the coach thought of the last decision. */
    public record Feedback(Action taken, Action recommended, String situation) {
        public boolean followed() {
            return taken == recommended;
        }
    }

    public BlackjackTable(BigDecimal startingChips, Supplier<Shoe> newShoe) {
        this.startingChips = startingChips;
        this.chips = startingChips;
        this.newShoe = newShoe;
        this.shoe = newShoe.get();
    }

    // -----------------------------------------------------------------------------------------------------
    // The round
    // -----------------------------------------------------------------------------------------------------

    public void deal(BigDecimal bet) {
        if (inRound) {
            throw new InvalidInputException("Finish the hand you're playing first");
        }
        if (bet == null || bet.compareTo(MIN_BET) < 0 || bet.compareTo(MAX_BET) > 0 || bet.stripTrailingZeros().scale() > 0) {
            throw new InvalidInputException("Bets are whole chips, " + MIN_BET + " to " + MAX_BET + " at this table");
        }
        if (bet.compareTo(chips) > 0) {
            throw new InvalidInputException("You only have " + chips(chips) + " chips left");
        }
        String shuffled = "";
        if (shoe.needsShuffle()) {
            shoe = newShoe.get();
            shuffled = "New shoe shuffled. ";
        }

        chips = chips.subtract(bet);
        wagered = wagered.add(bet);
        hands.clear();
        dealer.clear();
        Hand hand = new Hand(bet);
        hands.add(hand);
        hand.cards.add(shoe.draw());
        dealer.add(shoe.draw());
        hand.cards.add(shoe.draw());
        dealer.add(shoe.draw());
        current = 0;
        inRound = true;
        holeRevealed = false;
        lastFeedback = null;

        Card up = dealer.get(0);
        boolean dealerBlackjack = Hand.totalOf(dealer) == 21;
        // The dealer peeks under an ace or a 10: with blackjack the round ends before anyone plays
        if ((up.isAce() || up.value() == 10) && dealerBlackjack) {
            holeRevealed = true;
            hand.done = true;
            settle();
            message = shuffled + (hand.blackjack() ? "Both blackjack: push, your bet comes back." : "Dealer has blackjack.");
            return;
        }
        if (hand.blackjack()) {
            hand.done = true;
            finishRound();
            message = shuffled + "Blackjack! Pays 3 to 2: +" + chips(hand.bet.multiply(new BigDecimal("1.5"))) + " chips.";
            return;
        }
        message = shuffled + "Your move.";
    }

    public void hit() {
        Hand hand = decide(Action.HIT);
        hand.cards.add(shoe.draw());
        if (hand.busted()) {
            hand.done = true;
            hand.outcome = Hand.Outcome.BUST;
        } else if (hand.total() == 21) {
            hand.done = true;
        }
        advance();
    }

    public void stand() {
        decide(Action.STAND).done = true;
        advance();
    }

    public void doubleDown() {
        if (!canDouble()) {
            throw new InvalidInputException(hands.isEmpty() || !inRound ? "No hand to double"
                    : "You can only double on your first two cards, with chips to match the bet");
        }
        Hand hand = decide(Action.DOUBLE);
        chips = chips.subtract(hand.bet);
        wagered = wagered.add(hand.bet);
        hand.bet = hand.bet.add(hand.bet);
        hand.doubled = true;
        hand.cards.add(shoe.draw());
        hand.done = true;
        if (hand.busted()) {
            hand.outcome = Hand.Outcome.BUST;
        }
        advance();
    }

    public void split() {
        if (!canSplit()) {
            throw new InvalidInputException("You can split two cards of the same value, up to " + MAX_HANDS
                    + " hands, with chips to match the bet");
        }
        Hand hand = decide(Action.SPLIT);
        chips = chips.subtract(hand.bet);
        wagered = wagered.add(hand.bet);
        Hand second = new Hand(hand.bet);
        second.cards.add(hand.cards.remove(1));
        hand.fromSplit = true;
        second.fromSplit = true;
        boolean aces = hand.cards.get(0).isAce();
        hand.cards.add(shoe.draw());
        second.cards.add(shoe.draw());
        hands.add(current + 1, second);
        if (aces) {
            // Split aces get one card each and that's it
            hand.splitAces = second.splitAces = true;
            hand.done = second.done = true;
        } else {
            hand.done = hand.total() == 21;
            second.done = second.total() == 21;
        }
        advance();
    }

    /** Records the decision against basic strategy, then hands back the hand it applies to. */
    private Hand decide(Action taken) {
        Hand hand = playingHand();
        Advice advice = advice();
        decisions++;
        if (advice.action() == taken) {
            decisionsByTheBook++;
        }
        lastFeedback = new Feedback(taken, advice.action(), advice.situation());
        return hand;
    }

    private Hand playingHand() {
        if (!inRound || current >= hands.size()) {
            throw new InvalidInputException("Deal a hand first");
        }
        return hands.get(current);
    }

    /** Next hand still to play, or the dealer's turn. */
    private void advance() {
        while (current < hands.size() && hands.get(current).done) {
            current++;
        }
        if (current >= hands.size()) {
            finishRound();
        } else {
            message = hands.size() > 1 ? "Playing hand " + (current + 1) + " of " + hands.size() + "." : "Your move.";
        }
    }

    /** The dealer turns the hole card and draws to 17 (unless every hand is already bust), then pays out. */
    private void finishRound() {
        holeRevealed = true;
        boolean anyLive = hands.stream().anyMatch(h -> !h.busted());
        boolean onlyBlackjack = hands.size() == 1 && hands.get(0).blackjack();
        if (anyLive && !onlyBlackjack) {
            while (Hand.totalOf(dealer) < 17) {
                dealer.add(shoe.draw());
            }
        }
        settle();
    }

    private void settle() {
        int dealerTotal = Hand.totalOf(dealer);
        boolean dealerBlackjack = dealer.size() == 2 && dealerTotal == 21;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal staked = BigDecimal.ZERO;
        for (Hand hand : hands) {
            staked = staked.add(hand.bet);
            if (hand.busted()) {
                hand.outcome = Hand.Outcome.BUST;
            } else if (hand.blackjack() && !dealerBlackjack) {
                hand.outcome = Hand.Outcome.BLACKJACK;
                hand.payout = hand.bet.add(hand.bet.multiply(new BigDecimal("1.5")));
            } else if (dealerBlackjack) {
                hand.outcome = hand.blackjack() ? Hand.Outcome.PUSH : Hand.Outcome.LOSE;
                hand.payout = hand.blackjack() ? hand.bet : BigDecimal.ZERO;
            } else if (dealerTotal > 21 || hand.total() > dealerTotal) {
                hand.outcome = Hand.Outcome.WIN;
                hand.payout = hand.bet.add(hand.bet);
            } else if (hand.total() == dealerTotal) {
                hand.outcome = Hand.Outcome.PUSH;
                hand.payout = hand.bet;
            } else {
                hand.outcome = Hand.Outcome.LOSE;
            }
            paid = paid.add(hand.payout);
        }
        chips = chips.add(paid);
        inRound = false;
        roundsPlayed++;

        BigDecimal net = paid.subtract(staked);
        String dealerSays = dealerTotal > 21 ? "Dealer busts with " + dealerTotal + ". "
                : holeRevealed ? "Dealer has " + dealerTotal + ". " : "";
        message = dealerSays + (net.signum() > 0 ? "You win " + chips(net) + " chips."
                : net.signum() < 0 ? "You lose " + chips(net.negate()) + " chips." : "Push: your bet comes back.");
    }

    // -----------------------------------------------------------------------------------------------------
    // What the player can do, and the coach
    // -----------------------------------------------------------------------------------------------------

    public boolean canDeal() {
        return !inRound && chips.compareTo(MIN_BET) >= 0;
    }

    public boolean canHitOrStand() {
        return inRound && current < hands.size();
    }

    public boolean canDouble() {
        if (!canHitOrStand()) {
            return false;
        }
        Hand hand = hands.get(current);
        return hand.cards.size() == 2 && !hand.splitAces && chips.compareTo(hand.bet) >= 0;
    }

    public boolean canSplit() {
        if (!canHitOrStand()) {
            return false;
        }
        Hand hand = hands.get(current);
        return hand.cards.size() == 2 && hand.cards.get(0).value() == hand.cards.get(1).value()
                && hands.size() < MAX_HANDS && !hand.splitAces && chips.compareTo(hand.bet) >= 0;
    }

    /** Basic strategy's play for the hand waiting on a decision; null between rounds. */
    public Advice advice() {
        if (!canHitOrStand()) {
            return null;
        }
        return BasicStrategy.advise(hands.get(current).cards, dealer.get(0), canDouble(), canSplit());
    }

    // -----------------------------------------------------------------------------------------------------
    // What the table shows
    // -----------------------------------------------------------------------------------------------------

    public BigDecimal chips() {
        return chips;
    }

    public BigDecimal startingChips() {
        return startingChips;
    }

    /** Chips in bets on the table right now. */
    public BigDecimal onTable() {
        return inRound ? hands.stream().map(h -> h.bet).reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;
    }

    public List<Hand> hands() {
        return List.copyOf(hands);
    }

    /** The dealer's cards, with the hole card as null until it is turned over. */
    public List<Card> dealerCards() {
        List<Card> shown = new ArrayList<>(dealer);
        if (!holeRevealed && shown.size() > 1) {
            shown.set(1, null);
        }
        return shown;
    }

    /** The dealer's total as far as the player can see. */
    public int dealerShowing() {
        return holeRevealed ? Hand.totalOf(dealer) : dealer.isEmpty() ? 0 : Hand.totalOf(List.of(dealer.get(0)));
    }

    public int currentHand() {
        return current;
    }

    public boolean inRound() {
        return inRound;
    }

    public String message() {
        return message;
    }

    public int roundsPlayed() {
        return roundsPlayed;
    }

    public BigDecimal wagered() {
        return wagered;
    }

    public int decisions() {
        return decisions;
    }

    public int decisionsByTheBook() {
        return decisionsByTheBook;
    }

    public Feedback lastFeedback() {
        return lastFeedback;
    }

    public int cardsLeftInShoe() {
        return shoe.remaining();
    }

    private static String chips(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() <= 0 ? amount.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
