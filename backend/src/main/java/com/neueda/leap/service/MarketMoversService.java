package com.neueda.leap.service;

import com.neueda.leap.model.dto.MarketMover;
import com.neueda.leap.model.dto.MarketMoversResponse;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Today's top US stock gainers and losers, read from Yahoo Finance's public gainers/losers pages.
 * Those pages render the table on the server, so each row is read straight from the HTML (no API key).
 * Re-read at most once a minute. If Yahoo changes its page layout, parseMovers finds no rows and the
 * last good copy keeps being served.
 */
@Service
public class MarketMoversService {
    static final String GAINERS_URL = "https://finance.yahoo.com/markets/stocks/gainers/";
    static final String LOSERS_URL = "https://finance.yahoo.com/markets/stocks/losers/";
    static final String SOURCE = "Yahoo Finance";

    private final ExternalPageFetcher fetcher;
    private final Clock clock;
    private final RefreshingCache<MarketMoversResponse> cache;

    @Autowired
    public MarketMoversService(ExternalPageFetcher fetcher) {
        this(fetcher, Clock.systemUTC());
    }

    MarketMoversService(ExternalPageFetcher fetcher, Clock clock) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.cache = new RefreshingCache<>("Top gainers and losers", Duration.ofMinutes(1), Duration.ofSeconds(30),
                clock, this::load);
    }

    public MarketMoversResponse getMovers() {
        return cache.get();
    }

    private MarketMoversResponse load() throws IOException {
        List<MarketMover> gainers = parseMovers(fetcher.get(GAINERS_URL));
        List<MarketMover> losers = parseMovers(fetcher.get(LOSERS_URL));
        if (gainers.isEmpty() || losers.isEmpty()) {
            throw new IOException("Yahoo Finance pages had no gainers or losers rows; has the page layout changed?");
        }
        return new MarketMoversResponse(gainers, losers, clock.instant(), SOURCE);
    }

    /**
     * The rows of a Yahoo Finance screener table. Each cell is marked data-testid-cell="..." (ticker,
     * companyshortname.raw, intradayprice, percentchange, ...); exact change figures are also in the
     * data-value of the row's fin-streamer elements, which are preferred over the rounded text.
     */
    static List<MarketMover> parseMovers(String html) {
        Document doc = Jsoup.parse(html);
        List<MarketMover> movers = new ArrayList<>();
        for (Element row : doc.select("tr[data-testid=data-table-v2-row]")) {
            String ticker = cellText(row, "ticker", ".symbol");
            BigDecimal price = number(cellText(row, "intradayprice", "[data-testid=change]"));
            if (ticker.isEmpty() || price == null) {
                continue;
            }
            movers.add(new MarketMover(
                    ticker,
                    cellText(row, "companyshortname.raw", null),
                    price,
                    streamerOr(row, "regularMarketChange", cellText(row, "intradaypricechange", null)),
                    streamerOr(row, "regularMarketChangePercent", cellText(row, "percentchange", null)),
                    volume(cellText(row, "dayvolume", null)),
                    emptyToNull(cellText(row, "intradaymarketcap", null))));
        }
        return movers;
    }

    /** The text of a row's cell, or of an element inside it; empty when missing. */
    private static String cellText(Element row, String cell, String inner) {
        Element td = row.selectFirst("td[data-testid-cell=" + cell + "]");
        if (td == null) {
            return "";
        }
        Element target = inner == null ? td : td.selectFirst(inner);
        return target == null ? "" : target.text().trim();
    }

    /** The exact figure from a fin-streamer's data-value, falling back to the cell's rounded text. */
    private static BigDecimal streamerOr(Element row, String field, String fallbackText) {
        Element streamer = row.selectFirst("fin-streamer[data-field=" + field + "]");
        BigDecimal exact = streamer == null ? null : number(streamer.attr("data-value"));
        return exact != null ? exact : number(fallbackText);
    }

    /** "+4.16", "-10.82%", "1,234.50" → a number; "--" or anything unreadable → null. */
    static BigDecimal number(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replace(",", "").replace("%", "").replace("+", "").trim();
        try {
            return cleaned.isEmpty() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "532,816" or "1.234M" → 532816 / 1234000. */
    static Long volume(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.replace(",", "").trim().toUpperCase();
        BigDecimal multiplier = switch (t.charAt(t.length() - 1)) {
            case 'K' -> BigDecimal.valueOf(1_000);
            case 'M' -> BigDecimal.valueOf(1_000_000);
            case 'B' -> BigDecimal.valueOf(1_000_000_000);
            default -> BigDecimal.ONE;
        };
        BigDecimal value = number(multiplier.equals(BigDecimal.ONE) ? t : t.substring(0, t.length() - 1));
        return value == null ? null : value.multiply(multiplier).longValue();
    }

    private static String emptyToNull(String text) {
        return text.isEmpty() || "--".equals(text) ? null : text;
    }
}
