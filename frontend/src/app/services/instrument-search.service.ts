/**
 * Service for instrument search requests.
 */
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

export interface InstrumentSearchRequest {
  query: string;
}

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
  private readonly HELD_INSTRUMENTS_URL = '/api/search/held-instruments';
  private readonly INSTRUMENT_PRICE_QUOTE_URL = '/api/search/instrument-price-quote';

  constructor(private http: HttpClient) {}

  searchHeldInstruments(query: string): Observable<TickerSearchResult[]> {
    const token = localStorage.getItem('jwtToken');
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    const request: InstrumentSearchRequest = { query };

    return this.http.post<TickerSearchResult[]>(this.HELD_INSTRUMENTS_URL, request, { headers })
      .pipe(
        catchError(error => {
          if (error.status === 401) {
            localStorage.removeItem('jwtToken');
          }

          return throwError(() => error);
        })
      );
  }

  searchInstrumentPriceQuote(query: string): Observable<PriceQuoteResult[]> {
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });

    const request: InstrumentSearchRequest = { query };

    return this.http.post<PriceQuoteResult[]>(this.INSTRUMENT_PRICE_QUOTE_URL, request, { headers })
      .pipe(
        catchError(error => throwError(() => error))
      );
  }
}