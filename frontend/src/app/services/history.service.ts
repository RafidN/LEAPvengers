/**
 * Historical data: orders, cash transactions, prices and portfolio snapshots.
 * Each call hits GET /api/history/{kind}?period=1d|7d|1m|1y.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

export type HistoryPeriod = '1d' | '7d' | '1m' | '1y';

export type OrderStatus = 'SUBMITTED' | 'WORKING' | 'ACCEPTED' | 'FILLED' | 'REJECTED' | 'FAILED' | 'CANCELED';

/** MARKET fills now; LIMIT and STOP wait as WORKING until the price reaches triggerPrice. */
export type OrderKind = 'MARKET' | 'LIMIT' | 'STOP';

export interface OrderHistoryResult {
  orderId: number;
  accountId?: number;
  ticker: string;
  orderType: string;        // BUY or SELL
  quantity: number;
  price: number;
  orderStatus: OrderStatus;
  orderDate: string;
  submittedAt: string;
  rejectionReason?: string; // REJECTED, FAILED and CANCELED orders only
  executionPrice?: number;  // FILLED orders only
  executedAt?: string;
  orderKind?: OrderKind;    // MARKET when absent
  triggerPrice?: number;    // the limit or stop price
  triggeredAt?: string;     // when a limit or stop order's price was reached
}

export interface CashTransactionResult {
  cashTransactionId: number;
  transactionType: string;  // DEPOSIT, WITHDRAWAL, or BUY / SELL for a filled order
  amount: number;
  transactionDate: string;
  transactionTime?: string; // when the cash moved
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
  assetClass: string;
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

  getOrderHistory(period?: HistoryPeriod): Observable<OrderHistoryResult[]> {
    return this.get<OrderHistoryResult[]>('orders', period);
  }

  getCashHistory(period?: HistoryPeriod): Observable<CashTransactionResult[]> {
    return this.get<CashTransactionResult[]>('cash', period);
  }

  /** Public endpoint, no sign-in needed. */
  getPriceHistory(ticker: string, period?: HistoryPeriod): Observable<PriceHistoryResult[]> {
    return this.get<PriceHistoryResult[]>('prices', period, { ticker });
  }

  getPortfolioHistory(period?: HistoryPeriod): Observable<PortfolioHistoryResult[]> {
    return this.get<PortfolioHistoryResult[]>('portfolio', period);
  }

  private get<T>(kind: string, period?: HistoryPeriod, params: Record<string, string> = {}): Observable<T> {
    const requestParams = period ? { ...params, period } : params;

    return this.http.get<T>(`/api/history/${kind}`, { params: requestParams }).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
