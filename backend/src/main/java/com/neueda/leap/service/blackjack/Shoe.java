package com.neueda.leap.service.blackjack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Six decks shuffled together, dealt from the top like a casino shoe: cards come out without replacement,
 * so what has been dealt changes what is left. A fresh shoe is shuffled once three quarters has been dealt
 * (the cut card), between rounds.
 */
public final class Shoe {
    public static final int DECKS = 6;
    /** Reshuffle once this share of the shoe has been dealt. */
    private static final double PENETRATION = 0.75;

    private final Deque<Card> cards;
    private final int size;
    private final boolean stacked;

    private Shoe(List<Card> cards, boolean stacked) {
        this.cards = new ArrayDeque<>(cards);
        this.size = cards.size();
        this.stacked = stacked;
    }

    /** A freshly shuffled six-deck shoe. */
    public static Shoe shuffled(Random random) {
        List<Card> cards = new ArrayList<>(DECKS * 52);
        for (int deck = 0; deck < DECKS; deck++) {
            for (char suit : Card.SUITS) {
                for (String rank : Card.RANKS) {
                    cards.add(new Card(rank, suit));
                }
            }
        }
        Collections.shuffle(cards, random);
        return new Shoe(cards, false);
    }

    /** Cards in exactly this order, never reshuffled: lets tests deal a known hand. */
    public static Shoe stacked(String... codes) {
        return new Shoe(java.util.Arrays.stream(codes).map(Card::of).toList(), true);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            throw new IllegalStateException("The shoe is empty");
        }
        return cards.pop();
    }

    /** True once the cut card has come out: shuffle a new shoe before the next round. */
    public boolean needsShuffle() {
        return !stacked && cards.size() < size * (1 - PENETRATION);
    }

    public int remaining() {
        return cards.size();
    }
}
