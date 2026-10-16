import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Observable } from 'rxjs';
import { expand, map, reduce } from 'rxjs/operators';

export interface AuditClient {
  clientId: number;
  firstName: string;
  lastName: string;
  email: string;
  segment: string;
}

export interface AuditAccount {
  accountId: number;
  openedDate: string;
  balance: number;
}

export interface AuditOrder {
  orderId: number;
  clientId: number;
  clientName: string;
  ticker: string;
  instrumentName: string;
  orderType: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  executionPrice?: number | null;
  orderStatus: string;
  rejectionReason?: string | null;
  orderDate: string;
  submittedAt: string;
  executedAt?: string | null;
}

export interface AuditOrderEvent {
  eventId: number;
  orderId: number;
  createdBy?: number | null;
  createdByUsername?: string | null;
  fromStatus?: string | null;
  toStatus: string;
  createdAt: string;
  details?: string | null;
}

export interface AuditOrderDetails {
  order: AuditOrder;
  client: AuditClient;
  account: AuditAccount;
  orderEvents: AuditOrderEvent[];
}

export interface AuditOrderPage {
  content: AuditOrder[];
  number: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  searchClients(query?: string): Observable<AuditClient[]> {
    let params = new HttpParams();
    if (query?.trim()) {
      params = params.set('query', query.trim());
    }
    return this.http.get<AuditClient[]>('/api/internal/audit/clients', { params });
  }

  getOrders(clientId?: number): Observable<AuditOrder[]> {
    let params = new HttpParams().set('page', 0).set('size', 100);
    if (clientId !== undefined) {
      params = params.set('clientId', clientId);
    }
    return this.http.get<AuditOrderPage>('/api/internal/audit/orders', { params }).pipe(
      expand(page => {
        if (page.number + 1 >= page.totalPages) {
          return EMPTY;
        }
        return this.http.get<AuditOrderPage>('/api/internal/audit/orders', {
          params: pageParams(page.number + 1, clientId)
        });
      }),
      reduce((orders, page) => orders.concat(page.content), [] as AuditOrder[])
    );
  }

  getOrderDetails(orderId: number): Observable<AuditOrderDetails> {
    return this.http.get<AuditOrderDetails>(`/api/internal/audit/orders/${orderId}`);
  }
}

function pageParams(page: number, clientId?: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', 100);
  return clientId === undefined ? params : params.set('clientId', clientId);
}