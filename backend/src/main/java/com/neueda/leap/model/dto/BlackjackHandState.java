package com.neueda.leap.model.dto;

import java.util.List;

/**
 * One player hand inside a blackjack round (supports split hands).
 */
public class BlackjackHandState {
    private List<String> cards;
    private Integer total;
    private String outcome;

    public BlackjackHandState() {
    }

    public BlackjackHandState(List<String> cards, Integer total, String outcome) {
        this.cards = cards;
        this.total = total;
        this.outcome = outcome;
    }

    public List<String> getCards() {
        return cards;
    }

    public void setCards(List<String> cards) {
        this.cards = cards;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}