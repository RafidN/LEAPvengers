/**
 * Risk Lab blackjack (GET/POST /api/games/blackjack/...). Play money only: nothing here touches an account.
 * Every call answers with the whole table.
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type BlackjackAction = 'HIT' | 'STAND' | 'DOUBLE' | 'SPLIT';
export type HandOutcome = 'PLAYING' | 'BLACKJACK' | 'WIN' | 'PUSH' | 'LOSE' | 'BUST';

export interface BlackjackHand {
  cards: string[];          // e.g. "10H", "AS"
  total: number;
  soft: boolean;            // an ace counting 11
  bet: number;
  doubled: boolean;
  outcome: HandOutcome;
  payout: number;           // chips back when settled, stake included
}

export interface BlackjackTableView {
  chips: number;
  onTable: number;
  inRound: boolean;
  hands: BlackjackHand[];
  currentHand?: number;
  dealerCards: (string | null)[];   // null = the face-down hole card
  dealerShowing: number;
  canDeal: boolean;
  canHit: boolean;
  canStand: boolean;
  canDouble: boolean;
  canSplit: boolean;
  message: string;
  cardsLeftInShoe: number;
  minBet: number;
  maxBet: number;
}

export interface BlackjackCoach {
  recommended?: BlackjackAction;   // the book's play for the hand waiting on a decision
  situation?: string;              // e.g. "hard 11 against a 6"
  lastTaken?: BlackjackAction;
  lastRecommended?: BlackjackAction;
  lastSituation?: string;
  lastFollowed: boolean;
}

export interface BlackjackStats {
  roundsPlayed: number;
  wagered: number;
  net: number;
  netPercent: number;
  decisions: number;
  decisionsByTheBook: number;
  houseEdgePercent: number;
  expectedLoss: number;
}

export interface BlackjackRealityCheck {
  seatedAt: string;
  minutesAtTable: number;
  portfolioAtStart: number;
  portfolioNow: number;
  portfolioChange: number;
  portfolioChangePercent: number;
  stakeTicker?: string;
  stakeQuantity?: number;
  stakePrice?: number;
  stakeQuantityNow?: number;
}

export interface BlackjackState {
  seated: boolean;
  table?: BlackjackTableView;
  coach?: BlackjackCoach;
  stats?: BlackjackStats;
  realityCheck?: BlackjackRealityCheck;
}

@Injectable({
  providedIn: 'root'
})
export class BlackjackService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/games/blackjack';

  state(): Observable<BlackjackState> {
    return this.http.get<BlackjackState>(this.base);
  }

  /** Fresh chips: 1,000, or the value of a holding when stakeTicker names one (pretend: nothing moves). */
  sitDown(stakeTicker?: string): Observable<BlackjackState> {
    return this.http.post<BlackjackState>(`${this.base}/seat`, { stakeTicker: stakeTicker || null });
  }

  leave(): Observable<void> {
    return this.http.delete<void>(`${this.base}/seat`);
  }

  deal(bet: number): Observable<BlackjackState> {
    return this.http.post<BlackjackState>(`${this.base}/deal`, { bet });
  }

  act(action: BlackjackAction): Observable<BlackjackState> {
    const path = { HIT: 'hit', STAND: 'stand', DOUBLE: 'double', SPLIT: 'split' }[action];
    return this.http.post<BlackjackState>(`${this.base}/${path}`, {});
  }
}
