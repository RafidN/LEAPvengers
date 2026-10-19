package com.neueda.leap.service.blackjack;

import com.neueda.leap.service.blackjack.BasicStrategy.Action;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spot checks against the published basic strategy chart for 6 decks, dealer stands on soft 17, double after split. */
class BasicStrategyTest {

    @ParameterizedTest(name = "{0} against {1} -> {2}")
    @CsvSource({
            // hard totals
            "10S 6H,   10D, HIT",
            "10S 6H,   6D,  STAND",
            "10S 2H,   3D,  HIT",
            "10S 2H,   4D,  STAND",
            "6S 5H,    6D,  DOUBLE",
            "6S 5H,    AD,  HIT",
            "6S 4H,    9D,  DOUBLE",
            "6S 4H,    10D, HIT",
            "5S 4H,    3D,  DOUBLE",
            "5S 4H,    2D,  HIT",
            "10S 7H,   AD,  STAND",
            // soft totals
            "AS 7H,    6D,  DOUBLE",
            "AS 7H,    2D,  STAND",
            "AS 7H,    9D,  HIT",
            "AS 8H,    6D,  STAND",
            "AS 6H,    3D,  DOUBLE",
            "AS 2H,    4D,  HIT",
            // pairs
            "AS AH,    10D, SPLIT",
            "8S 8H,    AD,  SPLIT",
            "10S KH,   6D,  STAND",
            "9S 9H,    7D,  STAND",
            "9S 9H,    8D,  SPLIT",
            "5S 5H,    9D,  DOUBLE",
            "2S 2H,    7D,  SPLIT",
            "2S 2H,    8D,  HIT",
            "4S 4H,    5D,  SPLIT",
            "6S 6H,    7D,  HIT"
    })
    void matchesTheChart(String hand, String dealer, Action expected) {
        assertEquals(expected, BasicStrategy.advise(cards(hand), Card.of(dealer), true, true).action());
    }

    @ParameterizedTest(name = "{0} against {1}, no double -> {2}")
    @CsvSource({
            "6S 5H, 6D, HIT",
            "AS 7H, 4D, STAND",
            "AS 6H, 4D, HIT"
    })
    void withoutTheChipsToDoubleItHitsOrStands(String hand, String dealer, Action expected) {
        assertEquals(expected, BasicStrategy.advise(cards(hand), Card.of(dealer), false, true).action());
    }

    @ParameterizedTest(name = "{0} against {1}, no split -> {2}")
    @CsvSource({
            "8S 8H, 10D, HIT",
            "AS AH, 6D,  HIT"
    })
    void pairsThatCantBeSplitPlayAsTotals(String hand, String dealer, Action expected) {
        assertEquals(expected, BasicStrategy.advise(cards(hand), Card.of(dealer), true, false).action());
    }

    private static List<Card> cards(String hand) {
        return Arrays.stream(hand.trim().split(" ")).map(Card::of).toList();
    }
}
