/**
 * The signed-in trader's orders: placing one (POST /api/orders), listing them
 * (GET /api/orders?period=1d|7d|1m|1y&order=desc|asc) and each order's timeline (GET /api/orders/{id}/events).
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { HistoryPeriod, OrderHistoryResult, OrderStatus } from './history.service';

export type OrderSide = 'BUY' | 'SELL';

export interface OrderRequest {
  accountId: number;
  ticker: string;
  orderType: OrderSide;
  quantity: number;
}

/**
 * Why the backend turned an order down (HTTP 422). orderId is set when the order was
 * still recorded as REJECTED, so its timeline can be shown.
 */
export interface OrderRejection {
  code: string;
  message: string;
  orderId?: number;
}

/** Statuses an order never leaves. */
export const FINAL_STATUSES: OrderStatus[] = ['FILLED', 'REJECTED', 'FAILED'];

/** One step in an order's life, e.g. ACCEPTED -> FILLED. */
export interface OrderEvent {
  eventId: number;
  orderId: number;
  createdBy?: number;        // absent for steps the system took
  createdByUsername?: string;
  fromStatus?: OrderStatus;  // absent for the first step
  toStatus: OrderStatus;
  createdAt: string;
  details?: string;
}

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  /**
   * Returns the order as ACCEPTED; the backend fills it (or marks it FAILED) about half a second later.
   * A broken trading rule errors with an HttpErrorResponse whose error is an OrderRejection.
   */
  placeOrder(request: OrderRequest): Observable<OrderHistoryResult> {
    return this.handleErrors(this.http.post<OrderHistoryResult[]>('/api/orders', request)).pipe(
      map(orders => orders[0])
    );
  }

  /** Newest first unless order is 'asc'. Omitting period returns every order. */
  getOrders(period?: HistoryPeriod, order: 'desc' | 'asc' = 'desc'): Observable<OrderHistoryResult[]> {
    const params: Record<string, string> = period ? { period, order } : { order };
    return this.handleErrors(this.http.get<OrderHistoryResult[]>('/api/orders', { params }));
  }

  /** Oldest first. The backend answers 403 for another trader's order. */
  getOrderEvents(orderId: number): Observable<OrderEvent[]> {
    return this.handleErrors(this.http.get<OrderEvent[]>(`/api/orders/${orderId}/events`));
  }

  private handleErrors<T>(request: Observable<T>): Observable<T> {
    return request.pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
