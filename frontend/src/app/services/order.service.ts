/**
 * The signed-in trader's orders: placing one (POST /api/orders), listing them
 * (GET /api/orders?period=1d|7d|1m|1y&order=desc|asc), each order's timeline (GET /api/orders/{id}/events)
 * and disputing one (POST /api/orders/{id}/disputes, GET /api/orders/disputes).
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

export type DisputeReason = 'WRONG_PRICE' | 'SHOULD_HAVE_FILLED' | 'NOT_PLACED_BY_ME' | 'OTHER';

/** What a client can say went wrong, in their words. Kept in step with DisputeService.REASONS. */
export const DISPUTE_REASONS: { value: DisputeReason; label: string }[] = [
  { value: 'SHOULD_HAVE_FILLED', label: 'Trade not executed as expected' },
  { value: 'WRONG_PRICE', label: 'The price was wrong' },
  { value: 'NOT_PLACED_BY_ME', label: "I didn't place this order" },
  { value: 'OTHER', label: 'Something else' }
];

export function disputeReasonLabel(reason: string): string {
  return DISPUTE_REASONS.find(r => r.value === reason)?.label ?? reason;
}

/** A client's dispute of one of their orders; Operations answers it with a resolution. */
export interface Dispute {
  disputeId: number;
  orderId: number;
  clientId?: number;         // Operations only
  clientName?: string;       // Operations only
  ticker: string;
  orderType: OrderSide;
  quantity: number;
  orderStatus: OrderStatus;
  reason: DisputeReason;
  message: string;
  status: 'OPEN' | 'RESOLVED';
  createdAt: string;
  raisedByUsername: string;
  resolution?: string;
  resolvedAt?: string;
  resolvedByUsername?: string;
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

  /** Answers 400 with a message to show (e.g. an open dispute already exists) and 403 for another trader's order. */
  raiseDispute(orderId: number, reason: DisputeReason, message: string): Observable<Dispute> {
    return this.handleErrors(this.http.post<Dispute>(`/api/orders/${orderId}/disputes`, { reason, message }));
  }

  /** The trader's disputes, newest first. */
  getDisputes(): Observable<Dispute[]> {
    return this.handleErrors(this.http.get<Dispute[]>('/api/orders/disputes'));
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
