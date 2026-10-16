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
}
