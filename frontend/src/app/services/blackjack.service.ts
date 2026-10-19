import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

export interface BlackjackPlayRequest {
  accountId: number;
  ticker: string;
  betQuantity: number;
}

export interface BlackjackRoundResult {
  roundId: string;
  status: 'IN_PROGRESS' | 'RESOLVED';
  accountId: number;
  ticker: string;
  betQuantity: number;
  priceSnapshot: number;
  wagerValue: number;
  outcomeSummary: 'PENDING' | 'WIN' | 'LOSE' | 'PUSH' | 'MIXED';
  playerHands: { cards: string[]; total: number; outcome: string }[];
  dealerCards: string[];
  dealerTotal: number;
  currentHandIndex?: number;
  canHit: boolean;
  canStand: boolean;
  canSplit: boolean;
  quantityBefore: number;
  quantityCurrent: number;
  quantityAfter?: number;
  playedAt: string;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class BlackjackService {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  start(request: BlackjackPlayRequest): Observable<BlackjackRoundResult> {
    return this.http.post<BlackjackRoundResult>('/api/games/blackjack/start', request).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }

  hit(roundId: string): Observable<BlackjackRoundResult> {
    return this.callAction(`/api/games/blackjack/${roundId}/hit`);
  }

  stand(roundId: string): Observable<BlackjackRoundResult> {
    return this.callAction(`/api/games/blackjack/${roundId}/stand`);
  }

  split(roundId: string): Observable<BlackjackRoundResult> {
    return this.callAction(`/api/games/blackjack/${roundId}/split`);
  }

  private callAction(url: string): Observable<BlackjackRoundResult> {
    return this.http.post<BlackjackRoundResult>(url, {}).pipe(
      catchError(error => {
        if (error.status === 401) {
          this.authService.logout();
        }
        return throwError(() => error);
      })
    );
  }
}