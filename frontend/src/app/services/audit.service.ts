import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { EMPTY, Observable } from 'rxjs';
import { expand, map, reduce } from 'rxjs/operators';
import { Dispute } from './order.service';

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
  // The quote this step was priced against (BR-15), when it involved a price
  quotePrice?: number | null;
  quoteTime?: string | null;
  quoteSource?: 'YAHOO_LIVE' | 'YAHOO_HISTORY' | 'SIMULATED' | null;
}

/** The cash a filled order moved: what it settled as. */
export interface AuditSettlement {
  cashTransactionId: number;
  transactionType: string;   // BUY (cash out) or SELL (cash in)
  amount: number;
  transactionDate: string;
  transactionTime?: string;
}

export interface AuditOrderDetails {
  order: AuditOrder;
  client: AuditClient;
  account: AuditAccount;
  orderEvents: AuditOrderEvent[];
  settlement?: AuditSettlement | null;
  disputes?: Dispute[];
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

  /** Clients' disputes, newest first: all of them, or only OPEN or RESOLVED ones. */
  getDisputes(status?: 'OPEN' | 'RESOLVED'): Observable<Dispute[]> {
    const params: Record<string, string> = status ? { status } : {};
    return this.http.get<Dispute[]>('/api/internal/audit/disputes', { params });
  }

  /** Closes a dispute with an answer the client sees on their History page. */
  resolveDispute(disputeId: number, resolution: string): Observable<Dispute> {
    return this.http.post<Dispute>(`/api/internal/audit/disputes/${disputeId}/resolve`, { resolution });
  }

  getOrderDetails(orderId: number): Observable<AuditOrderDetails> {
    return this.http.get<AuditOrderDetails>(`/api/internal/audit/orders/${orderId}`);
  }
}

function pageParams(page: number, clientId?: number): HttpParams {
  let params = new HttpParams().set('page', page).set('size', 100);
  return clientId === undefined ? params : params.set('clientId', clientId);
}