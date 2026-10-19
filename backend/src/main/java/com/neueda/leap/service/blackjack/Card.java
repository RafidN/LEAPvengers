package com.neueda.leap.service.blackjack;

import java.util.List;

/** A playing card, e.g. the ten of hearts is rank "10", suit 'H', code "10H". */
public record Card(String rank, char suit) {

    public static final List<String> RANKS = List.of("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K");
    public static final List<Character> SUITS = List.of('S', 'H', 'D', 'C');

    /** "10H" for the ten of hearts; tests use it to stack a shoe. */
    public static Card of(String code) {
        return new Card(code.substring(0, code.length() - 1), code.charAt(code.length() - 1));
    }

    /** Blackjack value: aces count 11 here (a hand counts them as 1 when 11 would bust it), pictures 10. */
    public int value() {
        return switch (rank) {
            case "A" -> 11;
            case "K", "Q", "J" -> 10;
            default -> Integer.parseInt(rank);
        };
    }

    public boolean isAce() {
        return rank.equals("A");
    }

    public String code() {
        return rank + suit;
    }
}
