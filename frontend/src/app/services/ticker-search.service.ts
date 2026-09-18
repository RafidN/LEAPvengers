/**
 * Injectable makes ticker search a singleton service that handles HTTP requests for a ticker search with JWT authentication.
 * Makes HTTP request with Bearer token in Authorization header
 * Observable handles asynchronous HTTP responses and errors.
 * Catch & Throw error is used to handle HTTP errors and propagate them appropriately.
 */
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
// This is an interface for TickerSearchRequest that can be used application-wide
// It represents the structure of the request body for the ticker search API.
export interface TickerSearchRequest {
  ticker: string;
}

// This is an interface for TickerSearchResult that can be used application-wide
// It represents the structure of the response body for the held-tickers search API.
export interface TickerSearchResult {
  holdingId: number;
  ticker: string;
  instrumentName: string;
  quantity: number;
  marketValue: number;
}

// This is an interface for PriceQuoteResult that can be used application-wide
// It represents the structure of the response body for the ticker-price-quote search API.
export interface PriceQuoteResult {
  ticker: string;
  instrumentName: string;
  currentPrice: number;
  assetClass: string;
  quoteTimestamp: string;
}

/**
 * Service for ticker search with JWT authentication
 * Makes HTTP request with Bearer token in Authorization header
 * Injectable makes this an Angular service that can be injected anywhere in the application. Which means there will be only one instance of this service shared across the entire app.
 * All ticker searches will be handled by this single instance.
 */
@Injectable({
  providedIn: 'root'
})

// TickerSearchService handles all ticker search operations and ensures JWT authentication is included in each request.
// Export makes this service available for use in other parts of the application.
export class TickerSearchService {
    /**
     * API URL for held tickers search endpoint. Requires JWT authentication.
     */
  private readonly HELD_TICKERS_URL = '/api/search/held-tickers';
  
    /**
     * API URL for ticker price quote endpoint. No authentication required.
     */
  private readonly TICKER_PRICE_QUOTE_URL = '/api/search/ticker-price-quote';
    // Constructor for injecting HttpClient, necessary for making HTTP requests.
  constructor(private http: HttpClient) {}

  /**
   * Search held tickers (user's portfolio holdings)
   * Automatically includes JWT token in Authorization header
   * Observable<TickerSearchResult[]> representing the asynchronous HTTP response containing the search results. Asynchronous for handling HTTP requests without blocking the main thread.
   * Search results will be returned as an array of TickerSearchResult objects with holdings information.
   */
  searchHeldTickers(ticker: string): Observable<TickerSearchResult[]> {
    
    // Content type is set to 'application/json' to indicate that the request body contains JSON data.
    const token = localStorage.getItem('jwtToken');
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });

    // Create the request object for the held tickers search API.
    const request: TickerSearchRequest = { ticker: ticker };

    // Make the HTTP POST request to the held-tickers search API with the request object and headers.
    // TickerSearchResult[] represents the expected response type from the API.

    /* Full HTTP request example:
    POST /api/search/held-tickers HTTP/1.1
    Authorization: Bearer eyJhbGc...
    Content-Type: application/json

    { "ticker": "AAPL" }*/
    return this.http.post<TickerSearchResult[]>(this.HELD_TICKERS_URL, request, { headers })
    // HTTP Post request goes through pipe for error handling. Any errors encountered during the request will be caught and processed accordingly.
      .pipe(
        catchError(error => {
          if (error.status === 401) {
            localStorage.removeItem('jwtToken');
            // Redirect to login
          }

    // Thrown error will be propagated to the caller for further handling.
          return throwError(() => error);
        })
      );
  }

  /**
   * Search ticker price quote (public market data)
   * No JWT authentication required - returns current market price for any ticker
   * Observable<PriceQuoteResult[]> representing the asynchronous HTTP response containing price data.
   * Search results will be returned as an array of PriceQuoteResult objects with current pricing.
   */
  searchTickerPriceQuote(ticker: string): Observable<PriceQuoteResult[]> {
    
    // Content type is set to 'application/json' to indicate that the request body contains JSON data.
    const headers = new HttpHeaders({
      'Content-Type': 'application/json'
    });

    // Create the request object for the ticker price quote search API.
    const request: TickerSearchRequest = { ticker: ticker };

    // Make the HTTP POST request to the ticker-price-quote search API with the request object and headers.
    // No Authorization header needed - this is public data.
    // PriceQuoteResult[] represents the expected response type from the API.

    /* Full HTTP request example:
    POST /api/search/ticker-price-quote HTTP/1.1
    Content-Type: application/json

    { "ticker": "AAPL" }*/
    return this.http.post<PriceQuoteResult[]>(this.TICKER_PRICE_QUOTE_URL, request, { headers })
    // HTTP Post request goes through pipe for error handling. Any errors encountered during the request will be caught and processed accordingly.
      .pipe(
        catchError(error => {
    // Thrown error will be propagated to the caller for further handling.
          return throwError(() => error);
        })
      );
  }
}
