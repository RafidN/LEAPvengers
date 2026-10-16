package com.neueda.leap.controller;

import com.neueda.leap.model.dto.MarketMoversResponse;
import com.neueda.leap.model.dto.MarketNewsArticle;
import com.neueda.leap.service.MarketMoversService;
import com.neueda.leap.service.MarketNewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Market-wide information from public sources, for every signed-in role: headlines and today's top
 * gainers and losers. A source that can't be reached (and has nothing cached) answers 503 through
 * GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/market")
public class MarketController {

    private final MarketNewsService marketNewsService;
    private final MarketMoversService marketMoversService;

    public MarketController(MarketNewsService marketNewsService, MarketMoversService marketMoversService) {
        this.marketNewsService = marketNewsService;
        this.marketMoversService = marketMoversService;
    }

    /** Up to 20 headlines, newest first. */
    @GetMapping("/news")
    public ResponseEntity<List<MarketNewsArticle>> getNews() {
        return ResponseEntity.ok(marketNewsService.getNews());
    }

    /** Today's top US gainers and losers from Yahoo Finance, biggest moves first. */
    @GetMapping("/movers")
    public ResponseEntity<MarketMoversResponse> getMovers() {
        return ResponseEntity.ok(marketMoversService.getMovers());
    }
}
