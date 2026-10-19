package com.neueda.leap.service.blackjack;

import com.neueda.leap.exception.InvalidInputException;
import com.neueda.leap.service.blackjack.BasicStrategy.Action;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The table deals from a stacked shoe, so every hand is known: cards come out player, dealer up, player,
 * dealer hole, then whatever is drawn next.
 */
class BlackjackTableTest {

    private static final BigDecimal TEN = new BigDecimal("10");

    private static BlackjackTable table(String... cards) {
        return new BlackjackTable(new BigDecimal("1000"), () -> Shoe.stacked(cards));
    }

    private static void assertChips(String expected, BlackjackTable table) {
        assertEquals(0, new BigDecimal(expected).compareTo(table.chips()), "chips were " + table.chips());
    }

    private static Hand.Outcome outcome(BlackjackTable table, int hand) {
        return table.hands().get(hand).outcome();
    }

    // Naturals and the dealer's peek

    @Test
    void blackjackPaysThreeToTwoAndTheDealerDoesNotDraw() {
        // Player A K, dealer 9 7: the dealer's 16 never gets a card
        BlackjackTable t = table("AS", "9H", "KD", "7C");
        t.deal(TEN);

        assertEquals(Hand.Outcome.BLACKJACK, outcome(t, 0));
        assertChips("1015", t);
        assertFalse(t.inRound());
        assertEquals(2, t.dealerCards().size());
    }

    @Test
    void dealerBlackjackUnderAnAceEndsTheRoundBeforeAnyonePlays() {
        BlackjackTable t = table("9S", "AH", "9D", "KC");
        t.deal(TEN);

        assertEquals(Hand.Outcome.LOSE, outcome(t, 0));
        assertFalse(t.canHitOrStand());
        assertChips("990", t);
        assertEquals("Dealer has blackjack.", t.message());
    }

    @Test
    void twoBlackjacksPush() {
        BlackjackTable t = table("AS", "AH", "KD", "QC");
        t.deal(TEN);

        assertEquals(Hand.Outcome.PUSH, outcome(t, 0));
        assertChips("1000", t);
    }

    @Test
    void holeCardStaysFaceDownUntilTheDealerPlays() {
        BlackjackTable t = table("9S", "10H", "9D", "7C");
        t.deal(TEN);

        assertNull(t.dealerCards().get(1));
        assertEquals(10, t.dealerShowing());

        t.stand();

        assertEquals("7C", t.dealerCards().get(1).code());
        assertEquals(17, t.dealerShowing());
    }

    // The dealer's play

    @Test
    void dealerStandsOnSoft17() {
        // Player 18 against A 6: the dealer stands, the 18 wins
        BlackjackTable t = table("10S", "AH", "8D", "6C");
        t.deal(TEN);
        t.stand();

        assertEquals(2, t.dealerCards().size());
        assertEquals(Hand.Outcome.WIN, outcome(t, 0));
        assertChips("1010", t);
    }

    @Test
    void dealerDrawsBelow17() {
        // Dealer 5 6 = 11 draws a 10 and makes 21
        BlackjackTable t = table("10S", "5H", "8D", "6C", "10H");
        t.deal(TEN);
        t.stand();

        assertEquals(21, t.dealerShowing());
        assertEquals(Hand.Outcome.LOSE, outcome(t, 0));
        assertChips("990", t);
    }

    @Test
    void bustLosesEvenThoughTheDealerWouldHaveBust() {
        // Player 16 hits a 9; the dealer's 16 isn't played, the bust has already lost
        BlackjackTable t = table("10S", "6H", "6D", "10C", "9H", "10D");
        t.deal(TEN);
        t.hit();

        assertEquals(Hand.Outcome.BUST, outcome(t, 0));
        assertEquals(2, t.dealerCards().size());
        assertChips("990", t);
    }

    @Test
    void equalTotalsPush() {
        BlackjackTable t = table("10S", "10H", "8D", "8C");
        t.deal(TEN);
        t.stand();

        assertEquals(Hand.Outcome.PUSH, outcome(t, 0));
        assertChips("1000", t);
    }

    // Doubling down

    @Test
    void doubleDownTakesOneCardForTwiceTheBet() {
        // 6 5 = 11 doubles into a 10 for 21; dealer 5 10 draws an 8 and busts
        BlackjackTable t = table("6S", "5H", "5D", "10C", "10H", "8C");
        t.deal(TEN);
        t.doubleDown();

        Hand hand = t.hands().get(0);
        assertTrue(hand.doubled());
        assertEquals(3, hand.cards().size());
        assertEquals(0, new BigDecimal("20").compareTo(hand.bet()));
        assertEquals(Hand.Outcome.WIN, hand.outcome());
        assertChips("1020", t);
    }

    @Test
    void doublingIsOnlyOnTheFirstTwoCards() {
        BlackjackTable t = table("2S", "10H", "3D", "7C", "2H", "10D");
        t.deal(TEN);
        t.hit();

        assertFalse(t.canDouble());
        assertThrows(InvalidInputException.class, t::doubleDown);
    }

    @Test
    void doublingNeedsChipsToMatchTheBet() {
        BlackjackTable t = new BlackjackTable(new BigDecimal("15"), () -> Shoe.stacked("6S", "5H", "5D", "10C"));
        t.deal(TEN);

        assertFalse(t.canDouble());
    }

    // Splitting

    @Test
    void splitPairsPlayAsTwoHandsAndCanDoubleAfter() {
        // 8 8 against a 10 7: hand 1 gets a 3 (11) and doubles into a 10; hand 2 gets a 10 (18)
        BlackjackTable t = table("8S", "10H", "8D", "7C", "3H", "10D", "10S");
        t.deal(TEN);
        t.split();
        assertEquals(2, t.hands().size());
        assertTrue(t.canDouble());
        t.doubleDown();
        t.stand();

        assertEquals(21, t.hands().get(0).total());
        assertEquals(Hand.Outcome.WIN, outcome(t, 0));
        assertEquals(Hand.Outcome.WIN, outcome(t, 1));
        // Staked 10 + 10 + 10 more for the double; back 40 + 20
        assertChips("1030", t);
    }

    @Test
    void splitAcesGetOneCardEachAndTwentyOneIsNotBlackjack() {
        BlackjackTable t = table("AS", "9H", "AD", "8C", "KH", "5D");
        t.deal(TEN);
        t.split();

        assertFalse(t.inRound());
        assertEquals(2, t.hands().get(0).cards().size());
        assertEquals(2, t.hands().get(1).cards().size());
        // A K after a split is 21, paid 1 to 1; A 5 = 16 loses to 17
        assertEquals(Hand.Outcome.WIN, outcome(t, 0));
        assertEquals(Hand.Outcome.LOSE, outcome(t, 1));
        assertChips("1000", t);
    }

    @Test
    void noMoreThanFourHands() {
        BlackjackTable t = table("8S", "10H", "8D", "7C", "8H", "2C", "8C", "3D", "8D", "4H");
        t.deal(TEN);
        t.split();
        t.split();
        t.split();

        assertEquals(4, t.hands().size());
        assertEquals(16, t.hands().get(0).total());
        assertFalse(t.canSplit());
    }

    // Bets

    @Test
    void betsMustBeWholeChipsWithinTheTableLimits() {
        BlackjackTable t = table("10S", "10H", "8D", "8C");

        assertThrows(InvalidInputException.class, () -> t.deal(new BigDecimal("4")));
        assertThrows(InvalidInputException.class, () -> t.deal(new BigDecimal("501")));
        assertThrows(InvalidInputException.class, () -> t.deal(new BigDecimal("10.5")));
    }

    @Test
    void cannotBetMoreThanTheChipsLeft() {
        BlackjackTable t = new BlackjackTable(new BigDecimal("20"), () -> Shoe.stacked("10S", "10H", "8D", "8C"));

        InvalidInputException e = assertThrows(InvalidInputException.class, () -> t.deal(new BigDecimal("25")));
        assertEquals("You only have 20 chips left", e.getMessage());
    }

    @Test
    void oneHandAtATime() {
        BlackjackTable t = table("10S", "10H", "8D", "8C");
        t.deal(TEN);

        assertThrows(InvalidInputException.class, () -> t.deal(TEN));
    }

    // The coach

    @Test
    void coachMarksADecisionAgainstBasicStrategy() {
        // Hard 11 against a 6: the book doubles; hitting instead counts as off the book
        BlackjackTable t = table("6S", "6H", "5D", "10C", "2H", "10D");
        t.deal(TEN);
        assertEquals(Action.DOUBLE, t.advice().action());

        t.hit();

        assertEquals(1, t.decisions());
        assertEquals(0, t.decisionsByTheBook());
        assertEquals(Action.HIT, t.lastFeedback().taken());
        assertEquals(Action.DOUBLE, t.lastFeedback().recommended());
        assertEquals("hard 11 against a 6", t.lastFeedback().situation());
    }

    @Test
    void coachCountsDecisionsByTheBook() {
        // Hard 17 against a 10: stand, as the book says
        BlackjackTable t = table("10S", "10H", "7D", "8C");
        t.deal(TEN);
        t.stand();

        assertEquals(1, t.decisionsByTheBook());
        assertTrue(t.lastFeedback().followed());
    }

    // The shoe

    @Test
    void anewShoeIsShuffledOnceTheCutCardComesOut() {
        AtomicInteger shoes = new AtomicInteger();
        Random random = new Random(42);
        BlackjackTable t = new BlackjackTable(new BigDecimal("100000"), () -> {
            shoes.incrementAndGet();
            return Shoe.shuffled(random);
        });

        // 312 cards, cut at 75%: a few dozen rounds of stand-only play get past it
        for (int round = 0; round < 80; round++) {
            t.deal(new BigDecimal("5"));
            while (t.canHitOrStand()) {
                t.stand();
            }
        }

        assertTrue(shoes.get() >= 2, "only " + shoes.get() + " shoe(s) used");
        assertTrue(t.cardsLeftInShoe() > 0);
    }
}
