package com.neueda.leap.service;

import com.neueda.leap.exception.ExternalDataUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The last value read from an outside source (a news feed, a Yahoo Finance page), re-read once it is
 * older than ttl. However many people have the page open, the source is read at most once per ttl.
 * If a re-read fails, the last good value keeps being served (and the source is left alone for
 * retryAfterFailure); with no good value yet, the failure is an ExternalDataUnavailableException (503).
 */
final class RefreshingCache<T> {
    private static final Logger logger = Logger.getLogger(RefreshingCache.class.getName());

    @FunctionalInterface
    interface Loader<T> {
        T load() throws Exception;
    }

    private final String description;
    private final Duration ttl;
    private final Duration retryAfterFailure;
    private final Clock clock;
    private final Loader<T> loader;

    private T value;
    private Instant loadedAt;
    private Instant lastFailure;

    RefreshingCache(String description, Duration ttl, Duration retryAfterFailure, Clock clock, Loader<T> loader) {
        this.description = description;
        this.ttl = ttl;
        this.retryAfterFailure = retryAfterFailure;
        this.clock = clock;
        this.loader = loader;
    }

    synchronized T get() {
        Instant now = clock.instant();
        if (value != null && loadedAt.plus(ttl).isAfter(now)) {
            return value;
        }
        if (value != null && lastFailure != null && lastFailure.plus(retryAfterFailure).isAfter(now)) {
            return value;
        }
        try {
            value = loader.load();
            loadedAt = now;
            lastFailure = null;
            return value;
        } catch (Exception e) {
            lastFailure = now;
            if (value != null) {
                logger.log(Level.WARNING, "Could not refresh " + description + "; still serving the copy from " + loadedAt, e);
                return value;
            }
            throw new ExternalDataUnavailableException(description + " is unavailable right now", e);
        }
    }
}
