/**
 * Market-wide information from public sources, read through the backend (the sites don't allow
 * browsers on other origins to read them directly). GET /api/market/news and GET /api/market/movers.
 * The backend caches both, so polling here doesn't add load on the sources.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface MarketNewsArticle {
  title: string;
  link: string;             // the full story on the publisher's site
  source: string;           // e.g. CNBC, MarketWatch
  publishedAt?: string;
  summary?: string;
}

/** A US stock among today's biggest movers. Not necessarily tradable in this app. */
export interface MarketMover {
  ticker: string;
  name: string;
  price: number;
  change: number;
  changePercent: number;    // 10.82 means +10.82%
  volume?: number;
  marketCap?: string;       // as the source shows it, e.g. "3.052B"
}

export interface MarketMovers {
  gainers: MarketMover[];
  losers: MarketMover[];
  asOf: string;
  source: string;
}

/** How often the backend re-reads its sources; polling faster would only get the cached copy. */
export const NEWS_REFRESH_MS = 5 * 60_000;
export const MOVERS_REFRESH_MS = 60_000;

@Injectable({
  providedIn: 'root'
})
export class MarketService {
  private readonly http = inject(HttpClient);

  getNews(): Observable<MarketNewsArticle[]> {
    return this.http.get<MarketNewsArticle[]>('/api/market/news');
  }

  getMovers(): Observable<MarketMovers> {
    return this.http.get<MarketMovers>('/api/market/movers');
  }
}
