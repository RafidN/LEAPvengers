package com.neueda.leap.model.dto;

import java.time.Instant;

/** One market news headline from a public RSS feed, linking to the full article on the publisher's site. */
public class MarketNewsArticle {
    private String title;
    private String link;
    private String source;
    private Instant publishedAt;
    private String summary;

    public MarketNewsArticle() {
    }

    public MarketNewsArticle(String title, String link, String source, Instant publishedAt, String summary) {
        this.title = title;
        this.link = link;
        this.source = source;
        this.publishedAt = publishedAt;
        this.summary = summary;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
