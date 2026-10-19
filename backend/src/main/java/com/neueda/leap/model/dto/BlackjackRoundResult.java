package com.neueda.leap.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload for one resolved blackjack hand.
 */
public class BlackjackRoundResult {
    private String roundId;
    private String status;
    private Integer accountId;
    private String ticker;
    private BigDecimal betQuantity;
    private BigDecimal priceSnapshot;
    private BigDecimal wagerValue;
    private String outcomeSummary;
    private List<BlackjackHandState> playerHands;
    private List<String> dealerCards;
    private Integer dealerTotal;
    private Integer currentHandIndex;
    private Boolean canHit;
    private Boolean canStand;
    private Boolean canSplit;
    private BigDecimal quantityBefore;
    private BigDecimal quantityCurrent;
    private BigDecimal quantityAfter;
    private LocalDateTime playedAt;
    private String message;

    public BlackjackRoundResult() {
    }

    public BlackjackRoundResult(String roundId, String status, Integer accountId, String ticker,
                                BigDecimal betQuantity, BigDecimal priceSnapshot, BigDecimal wagerValue,
                                String outcomeSummary, List<BlackjackHandState> playerHands,
                                List<String> dealerCards, Integer dealerTotal, Integer currentHandIndex,
                                Boolean canHit, Boolean canStand, Boolean canSplit,
                                BigDecimal quantityBefore, BigDecimal quantityCurrent, BigDecimal quantityAfter,
                                LocalDateTime playedAt, String message) {
        this.roundId = roundId;
        this.status = status;
        this.accountId = accountId;
        this.ticker = ticker;
        this.betQuantity = betQuantity;
        this.priceSnapshot = priceSnapshot;
        this.wagerValue = wagerValue;
        this.outcomeSummary = outcomeSummary;
        this.playerHands = playerHands;
        this.dealerCards = dealerCards;
        this.dealerTotal = dealerTotal;
        this.currentHandIndex = currentHandIndex;
        this.canHit = canHit;
        this.canStand = canStand;
        this.canSplit = canSplit;
        this.quantityBefore = quantityBefore;
        this.quantityCurrent = quantityCurrent;
        this.quantityAfter = quantityAfter;
        this.playedAt = playedAt;
        this.message = message;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public BigDecimal getBetQuantity() {
        return betQuantity;
    }

    public void setBetQuantity(BigDecimal betQuantity) {
        this.betQuantity = betQuantity;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public void setPriceSnapshot(BigDecimal priceSnapshot) {
        this.priceSnapshot = priceSnapshot;
    }

    public BigDecimal getWagerValue() {
        return wagerValue;
    }

    public void setWagerValue(BigDecimal wagerValue) {
        this.wagerValue = wagerValue;
    }

    public String getOutcomeSummary() {
        return outcomeSummary;
    }

    public void setOutcomeSummary(String outcomeSummary) {
        this.outcomeSummary = outcomeSummary;
    }

    public List<BlackjackHandState> getPlayerHands() {
        return playerHands;
    }

    public void setPlayerHands(List<BlackjackHandState> playerHands) {
        this.playerHands = playerHands;
    }

    public List<String> getDealerCards() {
        return dealerCards;
    }

    public void setDealerCards(List<String> dealerCards) {
        this.dealerCards = dealerCards;
    }

    public Integer getCurrentHandIndex() {
        return currentHandIndex;
    }

    public void setCurrentHandIndex(Integer currentHandIndex) {
        this.currentHandIndex = currentHandIndex;
    }

    public Boolean getCanHit() {
        return canHit;
    }

    public void setCanHit(Boolean canHit) {
        this.canHit = canHit;
    }

    public Boolean getCanStand() {
        return canStand;
    }

    public void setCanStand(Boolean canStand) {
        this.canStand = canStand;
    }

    public Boolean getCanSplit() {
        return canSplit;
    }

    public void setCanSplit(Boolean canSplit) {
        this.canSplit = canSplit;
    }

    public Integer getDealerTotal() {
        return dealerTotal;
    }

    public void setDealerTotal(Integer dealerTotal) {
        this.dealerTotal = dealerTotal;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public void setQuantityBefore(BigDecimal quantityBefore) {
        this.quantityBefore = quantityBefore;
    }

    public BigDecimal getQuantityCurrent() {
        return quantityCurrent;
    }

    public void setQuantityCurrent(BigDecimal quantityCurrent) {
        this.quantityCurrent = quantityCurrent;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public void setQuantityAfter(BigDecimal quantityAfter) {
        this.quantityAfter = quantityAfter;
    }

    public LocalDateTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(LocalDateTime playedAt) {
        this.playedAt = playedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}