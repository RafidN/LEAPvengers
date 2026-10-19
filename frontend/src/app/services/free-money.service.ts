import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

export interface FreeMoneyHitRequest {
  battleId?: string;
  accountId: number;
  ticker: string;
}

export interface FreeMoneyBattleStartResult {
  battleId: string;
  accountId: number;
  ticker: string;
  startingQuantity: number;
  startedAt: string;
  message: string;
}

export interface FreeMoneyHitResult {
  accountId: number;
  ticker: string;
  quantityBefore: number;
  quantityDeducted: number;
  quantityAfter: number;
  processedAt: string;
  message: string;
}

export interface FreeMoneyWinResult {
  accountId: number;
  ticker: string;
  quantityBefore: number;
  quantityAfter: number;
  multiplier: number;
  processedAt: string;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class FreeMoneyService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  startBattle(request: FreeMoneyHitRequest): Observable<FreeMoneyBattleStartResult> {
    return this.http.post<FreeMoneyBattleStartResult>('/api/games/free-money/start', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }

  registerHit(request: FreeMoneyHitRequest): Observable<FreeMoneyHitResult> {
    return this.http.post<FreeMoneyHitResult>('/api/games/free-money/hit', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }

  registerBossDefeat(request: FreeMoneyHitRequest): Observable<FreeMoneyWinResult> {
    return this.http.post<FreeMoneyWinResult>('/api/games/free-money/win', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }

  forfeitBattle(request: FreeMoneyHitRequest): Observable<FreeMoneyHitResult> {
    return this.http.post<FreeMoneyHitResult>('/api/games/free-money/forfeit', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}
