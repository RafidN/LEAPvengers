/**
 * History Service for querying historical financial data
 * Handles order history, cash transactions, price history, and portfolio snapshots
 * Supports time periods: past year, past month, past 7 days, past day, and today
 */
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

// ===== REQUEST INTERFACE =====
export interface TimePeriodRequest {
  // Most endpoints don't need a body, but kept for consistency
  ticker?: string;
}

// ===== ORDER HISTORY INTERFACES =====
export interface OrderHistoryResult {
  orderId: number;
  ticker: string;
  orderType: string;        // BUY or SELL
  quantity: number;
  price: number;
  orderStatus: string;      // Pending, Filled, Canceled, Rejected
  orderDate: string;
  submittedAt: string;
}

// ===== CASH TRANSACTION HISTORY INTERFACES =====
export interface CashTransactionResult {
  cashTransactionId: number;
  transactionType: string;  // DEPOSIT or WITHDRAWAL
  amount: number;
  transactionDate: string;
  runningBalance?: number;
}

// ===== PRICE HISTORY INTERFACES =====
export interface PriceHistoryResult {
  ticker: string;
  instrumentName: string;
  price: number;
  volume: number;
  quoteTimestamp: string;
}

// ===== PORTFOLIO HISTORY INTERFACES =====
export interface PortfolioHistoryResult {
  holdingId: number;
  ticker: string;
  instrumentName: string;
  quantity: number;
  price: number;
  totalValue: number;       // quantity × price
  asOfDate: string;
}

/**
 * Injectable service for all historical data queries
 * Provides methods for order history, cash transactions, price history, and portfolio snapshots
 */
@Injectable({
  providedIn: 'root'
})
export class HistoryService {

  private readonly BASE_URL = '/api/history';

  constructor(private http: HttpClient) { }

  // ===== HELPER: GET JWT TOKEN =====
  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('jwtToken');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  // ===== ORDER HISTORY METHODS (AUTHENTICATED) =====

  /**
   * Get user's order history from past year
   * @returns Observable<OrderHistoryResult[]>
   */
  getOrderHistoryPastYear(): Observable<OrderHistoryResult[]> {
    return this.http.post<OrderHistoryResult[]>(
      `${this.BASE_URL}/orders/past-year`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's order history from past month
   */
  getOrderHistoryPastMonth(): Observable<OrderHistoryResult[]> {
    return this.http.post<OrderHistoryResult[]>(
      `${this.BASE_URL}/orders/past-month`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's order history from past 7 days
   */
  getOrderHistoryPast7Days(): Observable<OrderHistoryResult[]> {
    return this.http.post<OrderHistoryResult[]>(
      `${this.BASE_URL}/orders/past-7-days`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's order history from past day
   */
  getOrderHistoryPastDay(): Observable<OrderHistoryResult[]> {
    return this.http.post<OrderHistoryResult[]>(
      `${this.BASE_URL}/orders/past-day`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's order history from today only
   */
  getOrderHistoryToday(): Observable<OrderHistoryResult[]> {
    return this.http.post<OrderHistoryResult[]>(
      `${this.BASE_URL}/orders/today`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  // ===== CASH TRANSACTION HISTORY METHODS (AUTHENTICATED) =====

  /**
   * Get user's cash transaction history from past year
   * @returns Observable<CashTransactionResult[]>
   */
  getCashHistoryPastYear(): Observable<CashTransactionResult[]> {
    return this.http.post<CashTransactionResult[]>(
      `${this.BASE_URL}/cash/past-year`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's cash transaction history from past month
   */
  getCashHistoryPastMonth(): Observable<CashTransactionResult[]> {
    return this.http.post<CashTransactionResult[]>(
      `${this.BASE_URL}/cash/past-month`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's cash transaction history from past 7 days
   */
  getCashHistoryPast7Days(): Observable<CashTransactionResult[]> {
    return this.http.post<CashTransactionResult[]>(
      `${this.BASE_URL}/cash/past-7-days`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's cash transaction history from past day
   */
  getCashHistoryPastDay(): Observable<CashTransactionResult[]> {
    return this.http.post<CashTransactionResult[]>(
      `${this.BASE_URL}/cash/past-day`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get user's cash transaction history from today only
   */
  getCashHistoryToday(): Observable<CashTransactionResult[]> {
    return this.http.post<CashTransactionResult[]>(
      `${this.BASE_URL}/cash/today`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  // ===== PRICE HISTORY METHODS (PUBLIC - NO AUTH) =====

  /**
   * Get historical price data from past year
   * No authentication required
   * @param ticker The ticker symbol
   * @returns Observable<PriceHistoryResult[]>
   */
  getPriceHistoryPastYear(ticker: string): Observable<PriceHistoryResult[]> {
    return this.http.post<PriceHistoryResult[]>(
      `${this.BASE_URL}/prices/past-year`,
      { ticker }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get historical price data from past month
   */
  getPriceHistoryPastMonth(ticker: string): Observable<PriceHistoryResult[]> {
    return this.http.post<PriceHistoryResult[]>(
      `${this.BASE_URL}/prices/past-month`,
      { ticker }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get historical price data from past 7 days
   */
  getPriceHistoryPast7Days(ticker: string): Observable<PriceHistoryResult[]> {
    return this.http.post<PriceHistoryResult[]>(
      `${this.BASE_URL}/prices/past-7-days`,
      { ticker }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get historical price data from past day
   */
  getPriceHistoryPastDay(ticker: string): Observable<PriceHistoryResult[]> {
    return this.http.post<PriceHistoryResult[]>(
      `${this.BASE_URL}/prices/past-day`,
      { ticker }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get historical price data from today only
   */
  getPriceHistoryToday(ticker: string): Observable<PriceHistoryResult[]> {
    return this.http.post<PriceHistoryResult[]>(
      `${this.BASE_URL}/prices/today`,
      { ticker }
    ).pipe(catchError(error => this.handleError(error)));
  }

  // ===== PORTFOLIO HISTORY METHODS (AUTHENTICATED) =====

  /**
   * Get portfolio holdings snapshot from past year
   * @returns Observable<PortfolioHistoryResult[]>
   */
  getPortfolioHistoryPastYear(): Observable<PortfolioHistoryResult[]> {
    return this.http.post<PortfolioHistoryResult[]>(
      `${this.BASE_URL}/portfolio/past-year`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get portfolio holdings snapshot from past month
   */
  getPortfolioHistoryPastMonth(): Observable<PortfolioHistoryResult[]> {
    return this.http.post<PortfolioHistoryResult[]>(
      `${this.BASE_URL}/portfolio/past-month`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get portfolio holdings snapshot from past 7 days
   */
  getPortfolioHistoryPast7Days(): Observable<PortfolioHistoryResult[]> {
    return this.http.post<PortfolioHistoryResult[]>(
      `${this.BASE_URL}/portfolio/past-7-days`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get portfolio holdings snapshot from past day
   */
  getPortfolioHistoryPastDay(): Observable<PortfolioHistoryResult[]> {
    return this.http.post<PortfolioHistoryResult[]>(
      `${this.BASE_URL}/portfolio/past-day`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  /**
   * Get portfolio holdings snapshot from today only
   */
  getPortfolioHistoryToday(): Observable<PortfolioHistoryResult[]> {
    return this.http.post<PortfolioHistoryResult[]>(
      `${this.BASE_URL}/portfolio/today`,
      {},
      { headers: this.getAuthHeaders() }
    ).pipe(catchError(error => this.handleError(error)));
  }

  // ===== ERROR HANDLING =====

  /**
   * Handle HTTP errors
   * Clears JWT token on 401 Unauthorized
   */
  private handleError(error: any) {
    if (error.status === 401) {
      localStorage.removeItem('jwtToken');
      // Could redirect to login here
    }
    return throwError(() => error);
  }
}
