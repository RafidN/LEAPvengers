/**
 * Tradable instruments and their latest price, by instrument id.
 * GET /api/instruments and GET /api/instruments/quotes/{instrumentId}.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * How often pages re-read prices. Matches the live quote generator, which writes each instrument's latest
 * real trade price every 3 seconds while its market is open (scripts/live_quote_generator.py --interval-seconds).
 */
export const PRICE_REFRESH_MS = 3_000;

export interface Instrument {
  instrumentId: number;
  ticker: string;
  instrumentName: string;
  assetClass: string;       // Equity, Crypto or FX
  market: string;           // US, UK or IN
}

export type QuoteSource = 'YAHOO_LIVE' | 'YAHOO_HISTORY' | 'SIMULATED';

export interface Quote {
  price: number;
  timestamp: string;
  source?: QuoteSource;
  delayMinutes?: number;       // how far behind real time this market's data is (15 for London and NSE)
  tradable?: boolean;          // an order would get past the tradability check right now
  marketOpen?: boolean;
  market?: string;             // e.g. "the London Stock Exchange"
  opensAt?: string;            // next opening, when closed
  closesAt?: string;           // coming close, when open (crypto never closes)
  notTradableReason?: string;  // why an order would be rejected now
}

@Injectable({
  providedIn: 'root'
})
export class InstrumentService {
  private readonly http = inject(HttpClient);

  getInstruments(): Observable<Instrument[]> {
    return this.http.get<Instrument[]>('/api/instruments');
  }

  /** The backend answers 404 when the instrument has no price yet. */
  getQuote(instrumentId: number): Observable<Quote> {
    return this.http.get<Quote>(`/api/instruments/quotes/${instrumentId}`);
  }
}
