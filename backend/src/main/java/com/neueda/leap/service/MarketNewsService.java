package com.neueda.leap.service;

import com.neueda.leap.model.dto.MarketNewsArticle;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stock market headlines from free public RSS feeds (CNBC Markets, MarketWatch Bulletins), merged,
 * newest first. Feeds are re-read at most every 5 minutes; one feed being down just leaves its stories out.
 */
@Service
public class MarketNewsService {
    private static final Logger logger = Logger.getLogger(MarketNewsService.class.getName());

    /** A public RSS feed and the name its stories are credited to. */
    record Feed(String source, String url) {
    }

    // Both feeds stick to markets; MarketWatch's Top Stories feed also carries personal-finance pieces
    static final List<Feed> FEEDS = List.of(
            new Feed("CNBC", "https://www.cnbc.com/id/20910258/device/rss/rss.html"),
            new Feed("MarketWatch", "https://feeds.content.dowjones.io/public/rss/mw_bulletins"));

    static final int MAX_ARTICLES = 20;
    private static final int MAX_SUMMARY_LENGTH = 240;

    private final ExternalPageFetcher fetcher;
    private final RefreshingCache<List<MarketNewsArticle>> cache;

    @Autowired
    public MarketNewsService(ExternalPageFetcher fetcher) {
        this(fetcher, Clock.systemUTC());
    }

    MarketNewsService(ExternalPageFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.cache = new RefreshingCache<>("Market news", Duration.ofMinutes(5), Duration.ofMinutes(1), clock,
                this::loadAllFeeds);
    }

    public List<MarketNewsArticle> getNews() {
        return cache.get();
    }

    private List<MarketNewsArticle> loadAllFeeds() throws IOException {
        List<MarketNewsArticle> articles = new ArrayList<>();
        for (Feed feed : FEEDS) {
            try {
                articles.addAll(parseFeed(fetcher.get(feed.url()), feed.source()));
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not read the " + feed.source() + " news feed", e);
            }
        }
        if (articles.isEmpty()) {
            throw new IOException("No news feed could be read");
        }
        return newestFirst(articles);
    }

    /** Newest first (undated last), one copy of each story, at most MAX_ARTICLES. */
    static List<MarketNewsArticle> newestFirst(List<MarketNewsArticle> articles) {
        // The same story can come from two feeds, or twice from one, under the same link or headline
        Set<String> seen = new HashSet<>();
        return articles.stream()
                .filter(a -> {
                    boolean newLink = seen.add("link:" + a.getLink());
                    boolean newTitle = seen.add("title:" + a.getTitle().toLowerCase());
                    return newLink && newTitle;
                })
                .sorted(Comparator.comparing(MarketNewsArticle::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_ARTICLES)
                .toList();
    }

    /** The stories in an RSS 2.0 feed. Sponsored items and items without a title or link are left out. */
    static List<MarketNewsArticle> parseFeed(String xml, String source) throws Exception {
        Document doc = secureBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = doc.getElementsByTagName("item");
        List<MarketNewsArticle> articles = new ArrayList<>();
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String title = text(item, "title");
            String link = text(item, "link");
            if (title.isEmpty() || link.isEmpty() || "true".equalsIgnoreCase(text(item, "sponsored"))) {
                continue;
            }
            articles.add(new MarketNewsArticle(title, link, source, parseDate(text(item, "pubDate")),
                    summary(text(item, "description"))));
        }
        return articles;
    }

    /** Descriptions can hold HTML; keep the plain text, cut to a readable length at a word break. */
    private static String summary(String description) {
        String text = Jsoup.parse(description).text().trim();
        if (text.length() <= MAX_SUMMARY_LENGTH) {
            return text.isEmpty() ? null : text;
        }
        int cut = text.lastIndexOf(' ', MAX_SUMMARY_LENGTH);
        return text.substring(0, cut > 0 ? cut : MAX_SUMMARY_LENGTH) + "…";
    }

    /** RSS dates look like "Thu, 15 Oct 2026 13:42:32 GMT". */
    private static Instant parseDate(String pubDate) {
        try {
            return pubDate.isEmpty() ? null : ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(Element item, String tag) {
        NodeList nodes = item.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    /** Feeds come from other sites, so DOCTYPEs and external entities are refused (no XXE). */
    private static DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        factory.setXIncludeAware(false);
        return factory.newDocumentBuilder();
    }
}
