/**
 * The signed-in trader's orders and each order's timeline.
 * GET /api/orders?period=1d|7d|1m|1y&order=desc|asc and GET /api/orders/{id}/events.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { HistoryPeriod, OrderHistoryResult, OrderStatus } from './history.service';

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
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
