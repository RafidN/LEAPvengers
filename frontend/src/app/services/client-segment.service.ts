/**
 * Service for querying client segmentation data.
 * Handles authenticated HTTP requests for analyst-facing client segment queries.
 */
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

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

  private readonly BASE_URL = '/api/clients';

  constructor(private http: HttpClient) { }

  private getAuthHeaders(): HttpHeaders {
    const token = localStorage.getItem('jwtToken');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  /**
   * Query client segments using optional threshold and segment filters.
   * Uses backend defaults when a field is omitted.
   */
  getClientSegments(request: ClientSegmentQueryRequest = {}): Observable<ClientSegmentResult[]> {
    return this.http.post<ClientSegmentResult[]>(
      `${this.BASE_URL}/segments`,
      request,
      { headers: this.getAuthHeaders() }
    ).pipe(
      catchError(error => {
        if (error.status === 401) {
          localStorage.removeItem('jwtToken');
        }

        return throwError(() => error);
      })
    );
  }
}