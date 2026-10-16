/**
 * The signed-in trader's accounts and their cash balances (GET /api/accounts, Trader only),
 * and one account's cash and holdings (GET /api/accounts/{id}/portfolio).
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AccountResponse {
  accountId: number;
  openedDate: string;
  balance: number;
}

export interface AccountHolding {
  ticker: string;
  instrumentName: string;
  assetClass: string;
  quantity: number;
  latestPrice: number;
  value: number;            // quantity × latestPrice
  priceTimestamp: string;
}

export interface AccountPortfolio {
  accountId: number;
  balance: number;          // cash
  holdings: AccountHolding[];
  totalBalance: number;     // cash + holdings value
}

/** All of the trader's accounts together at one moment. */
export interface AccountValuePoint {
  time: string;
  totalValue: number;       // cash + holdingsValue
  cash: number;
  holdingsValue: number;    // holdings at the prices of that moment
}

export type ValuePeriod = '1d' | '7d' | '1m' | '1y';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private readonly http = inject(HttpClient);

  getAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>('/api/accounts');
  }

  /** Oldest first; the last point is now (GET /api/accounts/value-history). */
  getValueHistory(period: ValuePeriod): Observable<AccountValuePoint[]> {
    return this.http.get<AccountValuePoint[]>('/api/accounts/value-history', { params: { period } });
  }

  /** The backend answers 403 for another trader's account. */
  getPortfolio(accountId: number): Observable<AccountPortfolio> {
    return this.http.get<AccountPortfolio>(`/api/accounts/${accountId}/portfolio`);
  }
}
