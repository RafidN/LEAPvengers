package com.neueda.leap.exception;

/** A third-party data source (news feeds, Yahoo Finance) could not be read. Answered with 503. */
public class ExternalDataUnavailableException extends RuntimeException {

    public ExternalDataUnavailableException(String message) {
        super(message);
    }

    public ExternalDataUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
