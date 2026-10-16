package com.neueda.leap.service;

import com.neueda.leap.exception.ExternalDataUnavailableException;
import com.neueda.leap.model.dto.MarketMover;
import com.neueda.leap.model.dto.MarketMoversResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketMoversServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-15T15:00:00Z");

    private ExternalPageFetcher fetcher;
    private MutableClock clock;
    private MarketMoversService service;
    private String page;

    @BeforeEach
    void setUp() throws IOException {
        fetcher = mock(ExternalPageFetcher.class);
        clock = new MutableClock(NOW);
        service = new MarketMoversService(fetcher, clock);
        page = fixture("market/yahoo-gainers.html");
    }

    @Test
    void readsEachRowOfTheYahooTable() {
        List<MarketMover> movers = MarketMoversService.parseMovers(page);

        // The third row has no price, so it is left out
        assertEquals(2, movers.size());
        MarketMover appian = movers.get(0);
        assertEquals("APPN", appian.getTicker());
        assertEquals("Appian Corporation", appian.getName());
        assertEquals(new BigDecimal("42.61"), appian.getPrice());
        // The exact figures from the fin-streamer elements, not the rounded text
        assertEquals(new BigDecimal("4.16"), appian.getChange());
        assertEquals(new BigDecimal("10.819245"), appian.getChangePercent());
        assertEquals(532_816L, appian.getVolume());
        assertEquals("3.052B", appian.getMarketCap());
    }

    @Test
    void fallsBackToCellTextWithoutStreamers() {
        MarketMover loser = MarketMoversService.parseMovers(page).get(1);

        assertEquals(new BigDecimal("1204.50"), loser.getPrice());
        assertEquals(new BigDecimal("-35.20"), loser.getChange());
        assertEquals(new BigDecimal("-2.84"), loser.getChangePercent());
        assertEquals(1_200_000L, loser.getVolume());
        assertNull(loser.getMarketCap());
    }

    @Test
    void aPageWithoutTheTableHasNoRows() {
        assertEquals(List.of(), MarketMoversService.parseMovers("<html><body>Please enable JavaScript</body></html>"));
    }

    @Test
    void readsYahooAtMostOnceAMinute() throws IOException {
        when(fetcher.get(MarketMoversService.GAINERS_URL)).thenReturn(page);
        when(fetcher.get(MarketMoversService.LOSERS_URL)).thenReturn(page);

        MarketMoversResponse first = service.getMovers();
        clock.advance(Duration.ofSeconds(59));
        assertSame(first, service.getMovers());
        verify(fetcher, times(1)).get(MarketMoversService.GAINERS_URL);

        clock.advance(Duration.ofSeconds(2));
        service.getMovers();
        verify(fetcher, times(2)).get(MarketMoversService.GAINERS_URL);
        assertEquals(NOW, first.getAsOf());
        assertEquals("Yahoo Finance", first.getSource());
    }

    @Test
    void keepsServingTheLastGoodCopyWhenYahooFails() throws IOException {
        when(fetcher.get(MarketMoversService.GAINERS_URL)).thenReturn(page);
        when(fetcher.get(MarketMoversService.LOSERS_URL)).thenReturn(page);
        MarketMoversResponse good = service.getMovers();

        when(fetcher.get(MarketMoversService.GAINERS_URL)).thenThrow(new IOException("answered 429"));
        clock.advance(Duration.ofMinutes(2));

        assertSame(good, service.getMovers());
    }

    @Test
    void changedPageLayoutCountsAsAFailure() throws IOException {
        when(fetcher.get(MarketMoversService.GAINERS_URL)).thenReturn("<html></html>");
        when(fetcher.get(MarketMoversService.LOSERS_URL)).thenReturn("<html></html>");

        assertThrows(ExternalDataUnavailableException.class, () -> service.getMovers());
    }

    @Test
    void readsNumbersAsYahooWritesThem() {
        assertEquals(new BigDecimal("-10.82"), MarketMoversService.number("-10.82%"));
        assertEquals(new BigDecimal("1234.5"), MarketMoversService.number("+1,234.5"));
        assertNull(MarketMoversService.number("--"));
        assertEquals(3_400L, MarketMoversService.volume("3.4K"));
        assertEquals(2_000_000_000L, MarketMoversService.volume("2B"));
    }

    static String fixture(String path) throws IOException {
        try (var in = Objects.requireNonNull(MarketMoversServiceTest.class.getClassLoader().getResourceAsStream(path))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
