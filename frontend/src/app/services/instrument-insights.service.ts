/**
 * Instrument-level analytics for the analyst Insights page.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { ClientSegment } from './client-segment.service';

export interface InstrumentInsightsQueryRequest {
  lookbackDays?: number;
  segment?: ClientSegment;
  limit?: number;
}

export interface InstrumentInsightsResult {
  ticker: string;
  instrumentName: string;
  assetClass: string;
  currentPositionValue: number;
  recentFilledOrderCount: number;
  buyCount: number;
  sellCount: number;
  tradedClientCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class InstrumentInsightsService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  getInstrumentInsights(request: InstrumentInsightsQueryRequest = {}): Observable<InstrumentInsightsResult[]> {
    return this.http.post<InstrumentInsightsResult[]>('/api/insights/instruments', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}