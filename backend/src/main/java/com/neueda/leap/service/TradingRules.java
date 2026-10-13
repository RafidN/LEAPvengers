package com.neueda.leap.service;

import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The rules every order must pass before it is accepted, one method per rule.
 * Each method returns normally when the rule passes and throws a TradingRuleException
 * with a code and a readable message when it fails. No database access, so each rule
 * can be unit tested on its own.
 */
@Component
public class TradingRules {

    public static final String INVALID_QUANTITY = "INVALID_QUANTITY";
    public static final String UNKNOWN_INSTRUMENT = "UNKNOWN_INSTRUMENT";
    public static final String NO_PRICE = "NO_PRICE";
    public static final String STALE_PRICE = "STALE_PRICE";
    public static final String INSUFFICIENT_CASH = "INSUFFICIENT_CASH";
    public static final String INSUFFICIENT_HOLDINGS = "INSUFFICIENT_HOLDINGS";

    private final Clock clock;
    private final Duration maxPriceAge;

    @Autowired
    public TradingRules(@Value("${leap.trading.max-price-age:PT5M}") Duration maxPriceAge) {
        // Quote timestamps are written in the database server's local time, so compare in local time too
        this(Clock.systemDefaultZone(), maxPriceAge);
    }

    public TradingRules(Clock clock, Duration maxPriceAge) {
        this.clock = clock;
        this.maxPriceAge = maxPriceAge;
    }

    public void checkQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new TradingRuleException(INVALID_QUANTITY, "Quantity must be greater than 0");
        }
    }

    public void checkInstrumentExists(Instruments instrument, String ticker) {
        if (instrument == null) {
            throw new TradingRuleException(UNKNOWN_INSTRUMENT, "No instrument with ticker " + ticker);
        }
    }

    public void checkPriceIsRecent(LatestPriceQuotes quote) {
        if (quote == null || quote.getPrice() == null || quote.getQuoteTimestamp() == null) {
            throw new TradingRuleException(NO_PRICE, "No price is available for this instrument");
        }
        LocalDateTime oldestAllowed = LocalDateTime.now(clock).minus(maxPriceAge);
        if (quote.getQuoteTimestamp().isBefore(oldestAllowed)) {
            throw new TradingRuleException(STALE_PRICE,
                    "The latest price is from " + quote.getQuoteTimestamp() + ", older than the "
                            + maxPriceAge.toMinutes() + " minute limit");
        }
    }

    public void checkSufficientCash(BigDecimal balance, BigDecimal quantity, BigDecimal price) {
        BigDecimal cost = quantity.multiply(price);
        if (balance.compareTo(cost) < 0) {
            throw new TradingRuleException(INSUFFICIENT_CASH,
                    "This order costs " + cost + " but the account only has " + balance);
        }
    }

    public void checkSufficientHoldings(BigDecimal held, BigDecimal quantity) {
        BigDecimal owned = held == null ? BigDecimal.ZERO : held;
        if (owned.compareTo(quantity) < 0) {
            throw new TradingRuleException(INSUFFICIENT_HOLDINGS,
                    "Cannot sell " + quantity + " when the account only holds " + owned);
        }
    }
}
