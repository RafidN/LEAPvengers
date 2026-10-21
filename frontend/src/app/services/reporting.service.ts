/**
 * Internal trading activity reports (GET /api/internal/reports/activity, Analysts and Ops).
 * The backend answers from its reporting store, so these reports never compete with live trading.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ReportGrouping = 'day' | 'week' | 'month' | 'instrument' | 'assetClass' | 'segment';

export interface ActivityReportRow {
  key: string;              // e.g. 2026-10-12, NFLX, Crypto, Premier
  label: string;            // e.g. "Week of Oct 12, 2026"
  trades: number;
  buys: number;
  sells: number;
  tradedValue: number;      // USD
  activeClients: number;    // distinct clients who traded
}

export interface ActivityReport {
  from: string;
  to: string;
  groupBy: ReportGrouping;
  segment?: string;
  dataAsOf?: string;        // when the reporting store was last brought up to date
  rows: ActivityReportRow[];
  totals: ActivityReportRow;
}

export interface ActivityReportQuery {
  from: string;             // yyyy-MM-dd, inclusive
  to: string;
  groupBy: ReportGrouping;
  segment?: string;
}

export interface ReportDateRangeQuery {
  from: string;
  to: string;
}

export interface OrderHealthStatusCount {
  status: 'SUBMITTED' | 'ACCEPTED' | 'FILLED' | 'REJECTED' | 'FAILED';
  count: number;
}

export interface OrderHealthDailyRate {
  date: string;
  totalOrders: number;
  filledOrders: number;
  rejectedOrders: number;
  failedOrders: number;
  successRate: number;
  failureRate: number;
}

export interface OrderHealthReport {
  from: string;
  to: string;
  totalOrders: number;
  submittedOrders: number;
  acceptedOrders: number;
  filledOrders: number;
  rejectedOrders: number;
  failedOrders: number;
  successRate: number;
  failureRate: number;
  statusCounts: OrderHealthStatusCount[];
  dailyRates: OrderHealthDailyRate[];
}

@Injectable({
  providedIn: 'root'
})
export class ReportingService {
  private readonly http = inject(HttpClient);

  getActivity(query: ActivityReportQuery): Observable<ActivityReport> {
    const params: Record<string, string> = { from: query.from, to: query.to, groupBy: query.groupBy };
    if (query.segment) {
      params['segment'] = query.segment;
    }
    return this.http.get<ActivityReport>('/api/internal/reports/activity', { params });
  }

  getOrderHealth(query: ReportDateRangeQuery): Observable<OrderHealthReport> {
    return this.http.get<OrderHealthReport>('/api/internal/reports/order-health', {
      params: { from: query.from, to: query.to }
    });
  }
}
