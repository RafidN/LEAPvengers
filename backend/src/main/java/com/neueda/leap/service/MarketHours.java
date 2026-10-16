package com.neueda.leap.service;

import com.neueda.leap.model.Instruments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Whether an instrument's market is open right now (BR-05: "instrument currently tradable").
 * <ul>
 *   <li>Equities trade in their exchange's regular weekday session, in the exchange's own time zone:
 *       New York (NYSE/Nasdaq) 09:30-16:00, London (LSE) 08:00-16:30, India (NSE) 09:15-15:30.</li>
 *   <li>FX trades around the clock from Sunday 17:00 to Friday 17:00 New York time.</li>
 *   <li>Crypto never closes.</li>
 * </ul>
 * Exchange holidays aren't listed here. On a holiday no live quotes arrive, so the latest price goes stale
 * within a minute and TradingRules.checkPriceIsRecent rejects orders instead.
 * <p>
 * leap.trading.enforce-market-hours=false treats every market as open, for demos outside market hours with
 * the quote generator's --source simulated --all-hours mode.
 */
@Component
public class MarketHours {

    /** One exchange's regular weekday session. */
    private record Session(String venue, String city, ZoneId zone, LocalTime open, LocalTime close) {
    }

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final Session US_EQUITIES =
            new Session("the New York stock market", "New York", NEW_YORK, LocalTime.of(9, 30), LocalTime.of(16, 0));
    private static final Session UK_EQUITIES =
            new Session("the London Stock Exchange", "London", ZoneId.of("Europe/London"), LocalTime.of(8, 0), LocalTime.of(16, 30));
    private static final Session INDIAN_EQUITIES =
            new Session("the National Stock Exchange of India", "India", ZoneId.of("Asia/Kolkata"), LocalTime.of(9, 15), LocalTime.of(15, 30));
    private static final String FX_VENUE = "the foreign exchange market";
    private static final LocalTime FX_ROLLOVER = LocalTime.of(17, 0);

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'on' EEE d MMM", Locale.UK);

    /**
     * open: orders can be placed now. venue: who sets the hours, for messages ("the London Stock Exchange").
     * opensAt / closesAt: the next opening when closed, the coming close when open; null when it never closes.
     * closedReason: why it can't be traded, in words a client understands; null when open.
     */
    public record Status(boolean open, String venue, ZonedDateTime opensAt, ZonedDateTime closesAt, String closedReason) {
    }

    private final Clock clock;
    private final boolean enforced;

    @Autowired
    public MarketHours(@Value("${leap.trading.enforce-market-hours:true}") boolean enforced) {
        this(Clock.systemDefaultZone(), enforced);
    }

    public MarketHours(Clock clock, boolean enforced) {
        this.clock = clock;
        this.enforced = enforced;
    }

    public Status statusOf(Instruments instrument) {
        String assetClass = instrument.getAssetClass();
        if ("Crypto".equals(assetClass)) {
            return new Status(true, "the crypto market, which never closes", null, null, null);
        }
        if ("FX".equals(assetClass)) {
            return enforced ? fxStatus(ZonedDateTime.now(clock).withZoneSameInstant(NEW_YORK))
                    : new Status(true, FX_VENUE, null, null, null);
        }
        Session session = switch (instrument.getMarket() == null ? "" : instrument.getMarket()) {
            case "UK" -> UK_EQUITIES;
            case "IN" -> INDIAN_EQUITIES;
            default -> US_EQUITIES;
        };
        return enforced ? sessionStatus(session, ZonedDateTime.now(clock).withZoneSameInstant(session.zone()))
                : new Status(true, session.venue(), null, null, null);
    }

    private static Status sessionStatus(Session session, ZonedDateTime now) {
        LocalTime time = now.toLocalTime();
        if (isWeekday(now.getDayOfWeek()) && !time.isBefore(session.open()) && time.isBefore(session.close())) {
            return new Status(true, session.venue(), null, now.with(session.close()), null);
        }
        // The next weekday session start: later today if it hasn't opened yet, else the next weekday
        ZonedDateTime opens = now.with(session.open());
        if (!opens.isAfter(now) || !isWeekday(opens.getDayOfWeek())) {
            do {
                opens = opens.plusDays(1);
            } while (!isWeekday(opens.getDayOfWeek()));
        }
        String reason = capitalise(session.venue()) + " is closed. It opens at " + opens.format(WHEN)
                + " (" + session.city() + " time).";
        return new Status(false, session.venue(), opens, null, reason);
    }

    /** Open from Sunday 17:00 to Friday 17:00, New York time. */
    private static Status fxStatus(ZonedDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        boolean beforeRollover = now.toLocalTime().isBefore(FX_ROLLOVER);
        boolean closed = day == DayOfWeek.SATURDAY
                || (day == DayOfWeek.SUNDAY && beforeRollover)
                || (day == DayOfWeek.FRIDAY && !beforeRollover);
        if (!closed) {
            ZonedDateTime closes = now.with(FX_ROLLOVER);
            while (closes.getDayOfWeek() != DayOfWeek.FRIDAY || !closes.isAfter(now)) {
                closes = closes.plusDays(1);
            }
            return new Status(true, FX_VENUE, null, closes, null);
        }
        ZonedDateTime opens = now.with(FX_ROLLOVER);
        while (opens.getDayOfWeek() != DayOfWeek.SUNDAY || !opens.isAfter(now)) {
            opens = opens.plusDays(1);
        }
        String reason = capitalise(FX_VENUE) + " is closed for the weekend. It opens at " + opens.format(WHEN)
                + " (New York time).";
        return new Status(false, FX_VENUE, opens, null, reason);
    }

    private static boolean isWeekday(DayOfWeek day) {
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private static String capitalise(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
