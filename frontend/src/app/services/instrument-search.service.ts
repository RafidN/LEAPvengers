/**
 * Instrument search: the signed-in user's holdings, or price quotes for any instrument.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

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
}
