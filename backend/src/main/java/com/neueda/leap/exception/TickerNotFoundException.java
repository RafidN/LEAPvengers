package com.neueda.leap.exception;

public class TickerNotFoundException extends RuntimeException {
    public TickerNotFoundException(String message) {
        super(message);
    }
}