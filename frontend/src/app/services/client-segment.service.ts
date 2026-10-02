/**
 * Service for querying client segmentation data.
 * Handles authenticated HTTP requests for analyst-facing client segment queries.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

export type ClientSegment = 'Dormant' | 'Core' | 'Active' | 'Premier';

export interface ClientSegmentQueryRequest {
  segment?: ClientSegment;
  lookbackDays?: number;
  dormantMaxPortfolioValue?: number;
  dormantMaxOrderCount?: number;
  premierMinPortfolioValue?: number;
  activeMinOrderCount?: number;
}

export interface ClientSegmentResult {
  clientId: number;
  firstName: string;
  lastName: string;
  email: string;
  totalPortfolioValue: number;
  recentFilledOrderCount: number;
  segment: ClientSegment;
}

@Injectable({
  providedIn: 'root'
})
export class ClientSegmentService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  /**
   * Query client segments using optional threshold and segment filters.
   * Uses backend defaults when a field is omitted.
   */
  getClientSegments(request: ClientSegmentQueryRequest = {}): Observable<ClientSegmentResult[]> {
    return this.http.post<ClientSegmentResult[]>('/api/clients/segments', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
