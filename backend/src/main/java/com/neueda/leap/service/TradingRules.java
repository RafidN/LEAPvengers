package com.neueda.leap.service;

import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
    public static final String PRICE_CHANGED = "PRICE_CHANGED";
    public static final String NOT_TRADABLE = "NOT_TRADABLE";
    public static final String MARKET_CLOSED = "MARKET_CLOSED";
    public static final String PRICE_ALREADY_REACHED = "PRICE_ALREADY_REACHED";
    public static final String LIMIT_NOT_MET = "LIMIT_NOT_MET";
    public static final String NOT_WORKING = "NOT_WORKING";

    /** Fills now at the live price. */
    public static final String MARKET = "MARKET";
    /** Waits for a better price: a buy at or below the limit, a sell at or above it. */
    public static final String LIMIT = "LIMIT";
    /** Waits for the price to move against you: a sell (stop-loss) at or below the stop, a buy at or above it. */
    public static final String STOP = "STOP";

    private final Clock clock;
    private final Duration maxPriceAge;

    @Autowired
    public TradingRules(@Value("${leap.trading.max-price-age:PT1M}") Duration maxPriceAge) {
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

    /**
     * BR-05: the instrument must be tradable right now. The trading desk can suspend an instrument, and an
     * equity or FX pair can only be traded while its market is open (see MarketHours).
     */
    public void checkTradable(Instruments instrument, MarketHours.Status market) {
        if (!instrument.isTradable()) {
            throw new TradingRuleException(NOT_TRADABLE,
                    "Trading in " + instrument.getTicker() + " is suspended by the trading desk, so it can't be bought or sold right now");
        }
        if (!market.open()) {
            throw new TradingRuleException(MARKET_CLOSED,
                    instrument.getTicker() + " can't be traded right now. " + market.closedReason());
        }
    }

    /**
     * A limit or stop order can be placed while its market is closed, to wait for the price once it opens, but not
     * on an instrument the trading desk has suspended.
     */
    public void checkNotSuspended(Instruments instrument) {
        if (!instrument.isTradable()) {
            throw new TradingRuleException(NOT_TRADABLE,
                    "Trading in " + instrument.getTicker() + " is suspended by the trading desk, so it can't be bought or sold right now");
        }
    }

    public void checkPriceExists(LatestPriceQuotes quote) {
        if (quote == null || quote.getPrice() == null || quote.getQuoteTimestamp() == null) {
            throw new TradingRuleException(NO_PRICE, "No price is available for this instrument");
        }
    }

    public void checkPriceIsRecent(LatestPriceQuotes quote) {
        checkPriceIsRecent(quote, 0);
    }

    /**
     * BR-08: the latest quote must be current. delayMinutes is how far behind real time the instrument's
     * market data is licensed to be (Instruments.quoteDelayMinutes); a delayed feed gets that much extra.
     */
    public void checkPriceIsRecent(LatestPriceQuotes quote, int delayMinutes) {
        checkPriceExists(quote);
        Duration allowed = maxPriceAge.plusMinutes(delayMinutes);
        LocalDateTime oldestAllowed = LocalDateTime.now(clock).minus(allowed);
        if (quote.getQuoteTimestamp().isBefore(oldestAllowed)) {
            throw new TradingRuleException(STALE_PRICE,
                    "The latest price is from " + quote.getQuoteTimestamp().format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.US))
                            + ", older than the " + describe(allowed)
                            + " limit, so there is no current market price to trade at");
        }
    }

    /** The order was submitted at one price; if the latest price differs, the order's price is out of date. */
    public void checkPriceUnchanged(BigDecimal submittedPrice, LatestPriceQuotes quote) {
        checkPriceExists(quote);
        if (quote.getPrice().compareTo(submittedPrice) != 0) {
            String from = money(submittedPrice);
            String to = money(quote.getPrice());
            // Prices are shown to the cent, so a move of less than a cent would read "from $1.12 to $1.12"
            String move = from.equals(to) ? "moved by less than a cent from " + from : "moved from " + from + " to " + to;
            throw new TradingRuleException(PRICE_CHANGED,
                    "The price " + move + " while the order was waiting to be filled, so the order price is out of date");
        }
    }

    /**
     * A limit or stop order waits for a price it hasn't reached yet. One whose price the market is already at would
     * fill straight away, which is what a market order is for, so it is turned down with which side to put it on.
     */
    public void checkPriceNotReached(String side, String kind, BigDecimal triggerPrice, LatestPriceQuotes quote) {
        checkPriceExists(quote);
        if (!isReached(side, kind, triggerPrice, quote.getPrice())) {
            return;
        }
        boolean buy = side.equals("BUY");
        String what = kind.equals(LIMIT)
                ? (buy ? "A limit buy waits for the price to fall to your limit" : "A limit sell waits for the price to rise to your limit")
                : (buy ? "A stop buy waits for the price to rise to your stop" : "A stop-loss waits for the price to fall to your stop");
        boolean below = kind.equals(LIMIT) == buy;
        throw new TradingRuleException(PRICE_ALREADY_REACHED,
                what + ", so set it " + (below ? "below" : "above") + " the current price of " + money(quote.getPrice())
                        + ". To " + (buy ? "buy" : "sell") + " at the current price, place a market order");
    }

    /** A limit order is only filled at its limit or better; the price can move back between trigger and fill. */
    public void checkLimitStillMet(String side, BigDecimal limit, LatestPriceQuotes quote) {
        checkPriceExists(quote);
        if (!isReached(side, LIMIT, limit, quote.getPrice())) {
            throw new TradingRuleException(LIMIT_NOT_MET,
                    "The price moved to " + money(quote.getPrice()) + ", past your limit of " + money(limit)
                            + ", before the order could fill");
        }
    }

    /**
     * Whether a limit or stop order's price has been reached: a limit buy or stop sell when the price is at or below
     * triggerPrice, a limit sell or stop buy when it is at or above it. OrderRepository.findWorkingOrderIdsAtTriggerPrice
     * finds the same orders in the database.
     */
    public static boolean isReached(String side, String kind, BigDecimal triggerPrice, BigDecimal price) {
        boolean waitsForFall = kind.equals(LIMIT) == side.equals("BUY");
        int comparison = price.compareTo(triggerPrice);
        return waitsForFall ? comparison <= 0 : comparison >= 0;
    }

    /**
     * What a limit or stop order is waiting for, e.g. "sells if the price falls to $140.00 or lower (stop-loss)".
     */
    public static String describeTrigger(String side, String kind, BigDecimal triggerPrice) {
        boolean buy = side.equals("BUY");
        boolean waitsForFall = kind.equals(LIMIT) == buy;
        String described = (buy ? "buys" : "sells") + " if the price " + (waitsForFall ? "falls" : "rises") + " to "
                + money(triggerPrice) + " or " + (waitsForFall ? "lower" : "higher");
        return kind.equals(STOP) && !buy ? described + " (stop-loss)" : described;
    }

    /**
     * Cash for a buy is what the account holds less what its working buy orders could spend, so two orders can't
     * both count on the same cash.
     */
    public void checkSufficientCash(BigDecimal balance, BigDecimal heldForOrders, BigDecimal quantity, BigDecimal price) {
        if (heldForOrders == null || heldForOrders.signum() == 0) {
            checkSufficientCash(balance, quantity, price);
            return;
        }
        BigDecimal available = balance.subtract(heldForOrders);
        BigDecimal cost = quantity.multiply(price);
        if (available.compareTo(cost) < 0) {
            throw new TradingRuleException(INSUFFICIENT_CASH,
                    "This order costs " + money(cost) + " but the account only has " + money(available.max(BigDecimal.ZERO))
                            + " available, as " + money(heldForOrders) + " of its " + money(balance)
                            + " is held for working buy orders");
        }
    }

    public void checkSufficientCash(BigDecimal balance, BigDecimal quantity, BigDecimal price) {
        BigDecimal cost = quantity.multiply(price);
        if (balance.compareTo(cost) < 0) {
            throw new TradingRuleException(INSUFFICIENT_CASH,
                    "This order costs " + money(cost) + " but the account only has " + money(balance));
        }
    }

    public void checkSufficientHoldings(BigDecimal held, BigDecimal quantity) {
        BigDecimal owned = held == null ? BigDecimal.ZERO : held;
        if (owned.compareTo(quantity) < 0) {
            throw new TradingRuleException(INSUFFICIENT_HOLDINGS,
                    "Cannot sell " + quantity(quantity) + " when the account only holds " + quantity(owned));
        }
    }

    /**
     * Where a price came from, for the audit trail (BR-15), e.g. "the live Yahoo Finance quote of Oct 15, 14:25:32".
     */
    public static String describeQuote(LatestPriceQuotes quote, int delayMinutes) {
        String described = describeQuote(quote);
        return delayMinutes > 0 ? described + " (exchange data delayed " + delayMinutes + " min)" : described;
    }

    public static String describeQuote(LatestPriceQuotes quote) {
        String time = quote.getQuoteTimestamp().format(DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.US));
        String source = quote.getSource() == null ? "" : quote.getSource();
        return switch (source) {
            case "YAHOO_LIVE" -> "the live Yahoo Finance quote of " + time;
            case "YAHOO_HISTORY" -> "the Yahoo Finance historical price of " + time;
            case "SIMULATED" -> "the simulated (not real) quote of " + time;
            default -> "the quote of " + time;
        };
    }

    /** "1 minute", "30 seconds", "2 hours". */
    private static String describe(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds % 3600 == 0) {
            return plural(seconds / 3600, "hour");
        }
        return seconds % 60 == 0 ? plural(seconds / 60, "minute") : plural(seconds, "second");
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }

    /** Money for people to read, to the cent like everywhere on the site, e.g. $69,589,590.00. */
    public static String money(BigDecimal amount) {
        return new DecimalFormat("$#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }

    /** A quantity without padding zeros and at most 2 decimals, e.g. 2 rather than 2.0000. */
    public static String quantity(BigDecimal amount) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US)).format(amount);
    }
}
