/**
 * Historical data: orders, cash transactions, prices and portfolio snapshots.
 * Each call hits POST /api/history/{kind}/{period}.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

export type HistoryPeriod = 'past-year' | 'past-month' | 'past-7-days' | 'past-day' | 'today';

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

export interface CashTransactionResult {
  cashTransactionId: number;
  transactionType: string;  // DEPOSIT or WITHDRAWAL
  amount: number;
  transactionDate: string;
  runningBalance?: number;
}

export interface PriceHistoryResult {
  ticker: string;
  instrumentName: string;
  price: number;
  volume: number;
  quoteTimestamp: string;
}

export interface PortfolioHistoryResult {
  holdingId: number;
  ticker: string;
  instrumentName: string;
  quantity: number;
  price: number;
  totalValue: number;       // quantity × price
  asOfDate: string;
}

@Injectable({
  providedIn: 'root'
})
export class HistoryService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  getOrderHistory(period: HistoryPeriod): Observable<OrderHistoryResult[]> {
    return this.post<OrderHistoryResult[]>('orders', period);
  }

  getCashHistory(period: HistoryPeriod): Observable<CashTransactionResult[]> {
    return this.post<CashTransactionResult[]>('cash', period);
  }

  /** Public endpoint, no sign-in needed. */
  getPriceHistory(ticker: string, period: HistoryPeriod): Observable<PriceHistoryResult[]> {
    return this.post<PriceHistoryResult[]>('prices', period, { ticker });
  }

  getPortfolioHistory(period: HistoryPeriod): Observable<PortfolioHistoryResult[]> {
    return this.post<PortfolioHistoryResult[]>('portfolio', period);
  }

  private post<T>(kind: string, period: HistoryPeriod, body: object = {}): Observable<T> {
    return this.http.post<T>(`/api/history/${kind}/${period}`, body).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
