package com.neueda.leap.service;

import com.neueda.leap.exception.TradingRuleException;
import com.neueda.leap.model.Instruments;
import com.neueda.leap.model.LatestPriceQuotes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain unit tests (no database, no Spring): a pass and a fail case for each trading rule. */
class TradingRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 14, 0);
    private static final ZoneId ZONE = ZoneId.of("UTC");

    private final TradingRules rules = new TradingRules(
            Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE), Duration.ofMinutes(5));

    private static void assertFailsWith(String code, Executable rule) {
        TradingRuleException e = assertThrows(TradingRuleException.class, rule);
        assertEquals(code, e.getCode());
    }

    private static LatestPriceQuotes quote(String price, LocalDateTime at) {
        LatestPriceQuotes quote = new LatestPriceQuotes();
        quote.setInstrumentId(1);
        quote.setPrice(new BigDecimal(price));
        quote.setQuoteTimestamp(at);
        return quote;
    }

    // Quantity above zero

    @Test
    void quantityAboveZeroPasses() {
        assertDoesNotThrow(() -> rules.checkQuantity(new BigDecimal("0.5")));
    }

    @Test
    void zeroQuantityFails() {
        assertFailsWith(TradingRules.INVALID_QUANTITY, () -> rules.checkQuantity(BigDecimal.ZERO));
    }

    @Test
    void negativeQuantityFails() {
        assertFailsWith(TradingRules.INVALID_QUANTITY, () -> rules.checkQuantity(new BigDecimal("-2")));
    }

    @Test
    void missingQuantityFails() {
        assertFailsWith(TradingRules.INVALID_QUANTITY, () -> rules.checkQuantity(null));
    }

    // Instrument exists

    @Test
    void knownInstrumentPasses() {
        Instruments nflx = new Instruments(1, "NFLX", "Netflix", "Equity", "US");
        assertDoesNotThrow(() -> rules.checkInstrumentExists(nflx, "NFLX"));
    }

    @Test
    void unknownInstrumentFails() {
        assertFailsWith(TradingRules.UNKNOWN_INSTRUMENT, () -> rules.checkInstrumentExists(null, "NOPE"));
    }

    // A price exists and is recent enough

    @Test
    void freshPricePasses() {
        assertDoesNotThrow(() -> rules.checkPriceIsRecent(quote("100", NOW.minusSeconds(15))));
    }

    @Test
    void priceExactlyAtTheLimitPasses() {
        assertDoesNotThrow(() -> rules.checkPriceIsRecent(quote("100", NOW.minusMinutes(5))));
    }

    @Test
    void missingPriceFails() {
        assertFailsWith(TradingRules.NO_PRICE, () -> rules.checkPriceIsRecent(null));
    }

    @Test
    void anyPricePassesTheExistsCheckHoweverOld() {
        assertDoesNotThrow(() -> rules.checkPriceExists(quote("100", NOW.minusDays(30))));
    }

    @Test
    void missingPriceFailsTheExistsCheck() {
        assertFailsWith(TradingRules.NO_PRICE, () -> rules.checkPriceExists(null));
    }

    @Test
    void stalePriceFails() {
        assertFailsWith(TradingRules.STALE_PRICE,
                () -> rules.checkPriceIsRecent(quote("100", NOW.minusMinutes(5).minusSeconds(1))));
    }

    @Test
    void delayedFeedGetsItsDelayOnTopOfTheLimit() {
        // London prices arrive 15 minutes late on the free feed: 5 + 15 minutes is allowed, no more
        assertDoesNotThrow(() -> rules.checkPriceIsRecent(quote("100", NOW.minusMinutes(20)), 15));
        TradingRuleException e = assertThrows(TradingRuleException.class,
                () -> rules.checkPriceIsRecent(quote("100", NOW.minusMinutes(20).minusSeconds(1)), 15));
        assertEquals(TradingRules.STALE_PRICE, e.getCode());
        assertTrue(e.getMessage().contains("older than the 20 minutes limit"), e.getMessage());
    }

    // Tradable right now (BR-05)

    @Test
    void openMarketAndTradableInstrumentPasses() {
        Instruments nflx = new Instruments("NFLX", "Netflix", "Equity", "US");
        assertDoesNotThrow(() -> rules.checkTradable(nflx, new MarketHours.Status(true, "the New York stock market", null, null, null)));
    }

    @Test
    void closedMarketFailsWithItsReason() {
        Instruments tcs = new Instruments("TCS.NS", "Tata Consultancy", "Equity", "IN");
        TradingRuleException e = assertThrows(TradingRuleException.class, () -> rules.checkTradable(tcs,
                new MarketHours.Status(false, "the National Stock Exchange of India", null, null, "The NSE is closed.")));
        assertEquals(TradingRules.MARKET_CLOSED, e.getCode());
        assertEquals("TCS.NS can't be traded right now. The NSE is closed.", e.getMessage());
    }

    @Test
    void suspendedInstrumentFailsEvenWhileItsMarketIsOpen() {
        Instruments nflx = new Instruments("NFLX", "Netflix", "Equity", "US");
        nflx.setTradable(false);
        assertFailsWith(TradingRules.NOT_TRADABLE,
                () -> rules.checkTradable(nflx, new MarketHours.Status(true, "the New York stock market", null, null, null)));
    }

    // Price unchanged since the order was submitted

    @Test
    void samePriceAtConfirmationPasses() {
        assertDoesNotThrow(() -> rules.checkPriceUnchanged(new BigDecimal("100.00"), quote("100", NOW)));
    }

    @Test
    void movedPriceAtConfirmationFails() {
        assertFailsWith(TradingRules.PRICE_CHANGED,
                () -> rules.checkPriceUnchanged(new BigDecimal("100"), quote("100.01", NOW)));
    }

    @Test
    void missingPriceAtConfirmationFails() {
        assertFailsWith(TradingRules.NO_PRICE, () -> rules.checkPriceUnchanged(new BigDecimal("100"), null));
    }

    // Enough cash for a buy

    @Test
    void buyWithinBalancePasses() {
        assertDoesNotThrow(() -> rules.checkSufficientCash(
                new BigDecimal("1000"), new BigDecimal("10"), new BigDecimal("99.99")));
    }

    @Test
    void buyCostingExactlyTheBalancePasses() {
        assertDoesNotThrow(() -> rules.checkSufficientCash(
                new BigDecimal("1000"), new BigDecimal("10"), new BigDecimal("100")));
    }

    @Test
    void buyAboveBalanceFails() {
        assertFailsWith(TradingRules.INSUFFICIENT_CASH, () -> rules.checkSufficientCash(
                new BigDecimal("1000"), new BigDecimal("10"), new BigDecimal("100.01")));
    }

    // Enough holdings for a sell

    @Test
    void sellWithinHoldingsPasses() {
        assertDoesNotThrow(() -> rules.checkSufficientHoldings(new BigDecimal("6"), new BigDecimal("6")));
    }

    @Test
    void fractionalSellWithinHoldingsPasses() {
        assertDoesNotThrow(() -> rules.checkSufficientHoldings(new BigDecimal("0.5"), new BigDecimal("0.25")));
    }

    @Test
    void sellAboveHoldingsFails() {
        assertFailsWith(TradingRules.INSUFFICIENT_HOLDINGS,
                () -> rules.checkSufficientHoldings(new BigDecimal("6"), new BigDecimal("6.0001")));
    }

    @Test
    void sellWithNoHoldingFails() {
        assertFailsWith(TradingRules.INSUFFICIENT_HOLDINGS,
                () -> rules.checkSufficientHoldings(null, BigDecimal.ONE));
    }

    // Limit and stop orders

    @Test
    void limitBuysAndStopSellsAreReachedAtOrBelowTheirPrice() {
        BigDecimal at = new BigDecimal("100");
        assertTrue(TradingRules.isReached("BUY", TradingRules.LIMIT, at, new BigDecimal("100")));
        assertTrue(TradingRules.isReached("BUY", TradingRules.LIMIT, at, new BigDecimal("99.99")));
        assertFalse(TradingRules.isReached("BUY", TradingRules.LIMIT, at, new BigDecimal("100.01")));
        assertTrue(TradingRules.isReached("SELL", TradingRules.STOP, at, new BigDecimal("99")));
        assertFalse(TradingRules.isReached("SELL", TradingRules.STOP, at, new BigDecimal("101")));
    }

    @Test
    void limitSellsAndStopBuysAreReachedAtOrAboveTheirPrice() {
        BigDecimal at = new BigDecimal("100");
        assertTrue(TradingRules.isReached("SELL", TradingRules.LIMIT, at, new BigDecimal("100")));
        assertFalse(TradingRules.isReached("SELL", TradingRules.LIMIT, at, new BigDecimal("99.99")));
        assertTrue(TradingRules.isReached("BUY", TradingRules.STOP, at, new BigDecimal("100.5")));
        assertFalse(TradingRules.isReached("BUY", TradingRules.STOP, at, new BigDecimal("99")));
    }

    @Test
    void limitBuyBelowTheMarketPasses() {
        assertDoesNotThrow(() -> rules.checkPriceNotReached("BUY", TradingRules.LIMIT, new BigDecimal("95"), quote("100", NOW)));
    }

    @Test
    void stopLossAboveTheMarketFailsSayingToPutItBelow() {
        TradingRuleException e = assertThrows(TradingRuleException.class,
                () -> rules.checkPriceNotReached("SELL", TradingRules.STOP, new BigDecimal("101"), quote("100", NOW)));
        assertEquals(TradingRules.PRICE_ALREADY_REACHED, e.getCode());
        assertEquals("A stop-loss waits for the price to fall to your stop, so set it below the current price of $100.00."
                + " To sell at the current price, place a market order", e.getMessage());
    }

    @Test
    void limitSellBelowTheMarketFailsSayingToPutItAbove() {
        TradingRuleException e = assertThrows(TradingRuleException.class,
                () -> rules.checkPriceNotReached("SELL", TradingRules.LIMIT, new BigDecimal("99"), quote("100", NOW)));
        assertTrue(e.getMessage().contains("above the current price of $100.00"), e.getMessage());
    }

    @Test
    void limitStillMetAtABetterPricePasses() {
        assertDoesNotThrow(() -> rules.checkLimitStillMet("BUY", new BigDecimal("100"), quote("99.50", NOW)));
    }

    @Test
    void limitNoLongerMetFails() {
        assertFailsWith(TradingRules.LIMIT_NOT_MET,
                () -> rules.checkLimitStillMet("BUY", new BigDecimal("100"), quote("100.20", NOW)));
    }

    @Test
    void triggerDescriptionsSayWhatTheOrderWaitsFor() {
        assertEquals("sells if the price falls to $140.00 or lower (stop-loss)",
                TradingRules.describeTrigger("SELL", TradingRules.STOP, new BigDecimal("140")));
        assertEquals("buys if the price falls to $95.50 or lower",
                TradingRules.describeTrigger("BUY", TradingRules.LIMIT, new BigDecimal("95.5")));
        assertEquals("buys if the price rises to $210.00 or higher",
                TradingRules.describeTrigger("BUY", TradingRules.STOP, new BigDecimal("210")));
    }

    @Test
    void cashHeldForWorkingBuysCantBeSpentAgain() {
        // 1,000 cash, 700 of it held: a 400 buy doesn't fit
        TradingRuleException e = assertThrows(TradingRuleException.class, () -> rules.checkSufficientCash(
                new BigDecimal("1000"), new BigDecimal("700"), new BigDecimal("4"), new BigDecimal("100")));
        assertEquals(TradingRules.INSUFFICIENT_CASH, e.getCode());
        assertEquals("This order costs $400.00 but the account only has $300.00 available, as $700.00 of its $1,000.00"
                + " is held for working buy orders", e.getMessage());
        assertDoesNotThrow(() -> rules.checkSufficientCash(
                new BigDecimal("1000"), new BigDecimal("700"), new BigDecimal("3"), new BigDecimal("100")));
    }
}
