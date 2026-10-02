/**
 * The signed-in trader's accounts and their cash balances (GET /api/accounts, Trader only).
 */
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AccountResponse {
  accountId: number;
  openedDate: string;
  balance: number;
}

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private readonly http = inject(HttpClient);

  getAccounts(): Observable<AccountResponse[]> {
    return this.http.get<AccountResponse[]>('/api/accounts');
  }
}
