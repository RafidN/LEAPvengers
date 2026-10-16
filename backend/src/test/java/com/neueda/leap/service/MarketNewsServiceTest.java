package com.neueda.leap.service;

import com.neueda.leap.exception.ExternalDataUnavailableException;
import com.neueda.leap.model.dto.MarketNewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static com.neueda.leap.service.MarketMoversServiceTest.fixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketNewsServiceTest {
    private static final String CNBC = MarketNewsService.FEEDS.get(0).url();
    private static final String MARKETWATCH = MarketNewsService.FEEDS.get(1).url();

    private ExternalPageFetcher fetcher;
    private MarketNewsService service;

    @BeforeEach
    void setUp() {
        fetcher = mock(ExternalPageFetcher.class);
        service = new MarketNewsService(fetcher, new MarketMoversServiceTest.MutableClock(Instant.parse("2026-10-15T16:00:00Z")));
    }

    @Test
    void readsStoriesFromAnRssFeed() throws Exception {
        List<MarketNewsArticle> articles = MarketNewsService.parseFeed(fixture("market/cnbc-markets.xml"), "CNBC");

        // The sponsored item and the item without a link are left out
        assertEquals(2, articles.size());
        MarketNewsArticle first = articles.get(0);
        assertEquals("U.S. Treasury dismisses IMF debt warning", first.getTitle());
        assertEquals("https://www.cnbc.com/2026/10/15/treasury-imf.html", first.getLink());
        assertEquals("CNBC", first.getSource());
        assertEquals(Instant.parse("2026-10-15T13:42:32Z"), first.getPublishedAt());
        // HTML in a description is reduced to its text
        assertEquals("Producer prices rose sharply.", articles.get(1).getSummary());
    }

    @Test
    void mergesFeedsNewestFirstWithoutDuplicates() throws IOException {
        when(fetcher.get(CNBC)).thenReturn(fixture("market/cnbc-markets.xml"));
        when(fetcher.get(MARKETWATCH)).thenReturn(fixture("market/marketwatch-top.xml"));

        List<MarketNewsArticle> news = service.getNews();

        assertEquals(List.of(
                "Traders pile into bonds and defensive stocks as the “tech wreck” picks up steam",
                "Wholesale prices jumped 6.3% annually in September",
                "U.S. Treasury dismisses IMF debt warning",
                "An undated story"), news.stream().map(MarketNewsArticle::getTitle).toList());
        // MarketWatch's copy of the Treasury story is dropped: CNBC's has the same headline
        assertEquals("CNBC", news.get(2).getSource());
        assertNull(news.get(3).getPublishedAt());
    }

    @Test
    void oneFeedDownStillShowsTheOther() throws IOException {
        when(fetcher.get(CNBC)).thenThrow(new IOException("timed out"));
        when(fetcher.get(MARKETWATCH)).thenReturn(fixture("market/marketwatch-top.xml"));

        assertTrue(service.getNews().stream().allMatch(a -> a.getSource().equals("MarketWatch")));
    }

    @Test
    void everyFeedDownIsUnavailable() throws IOException {
        when(fetcher.get(anyString())).thenThrow(new IOException("offline"));

        assertThrows(ExternalDataUnavailableException.class, () -> service.getNews());
    }

    @Test
    void refusesFeedsWithADoctype() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><item><title>&x;</title><link>l</link></item></channel></rss>";

        assertThrows(Exception.class, () -> MarketNewsService.parseFeed(xxe, "Evil"));
    }
}
