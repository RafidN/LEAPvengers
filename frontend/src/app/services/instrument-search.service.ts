/**
 * Instrument search: the signed-in user's holdings, or price quotes for any instrument.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService } from './auth.service';

/** A spread of seeded instruments (US, UK, Indian stocks, crypto, FX) shown on the dashboard and markets pages. */
export const WATCHLIST = ['NFLX', 'AMD', 'SHEL', 'RELIANCE.NS', 'BTC-USD', 'EURUSD=X'];

export interface TickerSearchResult {
  holdingId: number;
  ticker: string;
  instrumentName: string;
  quantity: number;
  marketValue: number;
}

export interface PriceQuoteResult {
  ticker: string;
  instrumentName: string;
  currentPrice: number;
  assetClass: string;
  quoteTimestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class InstrumentSearchService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  searchHeldInstruments(query: string): Observable<TickerSearchResult[]> {
    return this.http.post<TickerSearchResult[]>('/api/search/held-instruments', { query }).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }

  searchInstrumentPriceQuote(query: string): Observable<PriceQuoteResult[]> {
    return this.http.post<PriceQuoteResult[]>('/api/search/instrument-price-quote', { query });
  }

  /** Latest quote for each exact ticker, in the order given. Tickers with no quote are left out. */
  getQuotes(tickers: string[]): Observable<PriceQuoteResult[]> {
    return forkJoin(
      tickers.map(ticker =>
        this.searchInstrumentPriceQuote(ticker).pipe(
          map(results => results.find(result => result.ticker === ticker)),
          catchError(() => of(undefined))
        )
      )
    ).pipe(map(quotes => quotes.filter((quote): quote is PriceQuoteResult => !!quote)));
  }
}
