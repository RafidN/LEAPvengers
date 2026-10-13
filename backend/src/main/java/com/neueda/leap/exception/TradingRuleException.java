package com.neueda.leap.exception;

/**
 * Thrown when an order breaks a trading rule.
 * The code (e.g. INSUFFICIENT_CASH) is stable for clients to match on; the message is for people.
 */
public class TradingRuleException extends RuntimeException {

    private final String code;

    public TradingRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
