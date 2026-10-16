package com.neueda.leap.exception;

import com.neueda.leap.model.LatestPriceQuotes;

/**
 * Thrown when an order breaks a trading rule.
 * The code (e.g. INSUFFICIENT_CASH) is stable for clients to match on; the message is for people.
 * orderId is set when the order was saved as REJECTED, so clients can open its timeline.
 * quote is the price the decision was made against, when there was one, for the order's audit trail.
 */
public class TradingRuleException extends RuntimeException {
    private final String code;
    private final Integer orderId;
    private final transient LatestPriceQuotes quote;

    public TradingRuleException(String code, String message) {
        this(code, message, null, null);
    }

    public TradingRuleException(String code, String message, Integer orderId) {
        this(code, message, orderId, null);
    }

    private TradingRuleException(String code, String message, Integer orderId, LatestPriceQuotes quote) {
        super(message);
        this.code = code;
        this.orderId = orderId;
        this.quote = quote;
    }

    /** The same rule failure, tied to the REJECTED order it was recorded on. */
    public TradingRuleException forOrder(Integer orderId) {
        return new TradingRuleException(code, getMessage(), orderId, quote);
    }

    /** The same rule failure, decided against this quote. */
    public TradingRuleException pricedFrom(LatestPriceQuotes quote) {
        return new TradingRuleException(code, getMessage(), orderId, quote);
    }

    public String getCode() {
        return code;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public LatestPriceQuotes getQuote() {
        return quote;
    }
}
