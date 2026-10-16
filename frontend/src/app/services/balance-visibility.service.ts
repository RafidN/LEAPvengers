/**
 * Whether each dashboard balance is shown or hidden behind its eye button. Everything starts hidden,
 * and AuthService hides everything again on each sign-in and sign-out. Kept in memory only, so
 * revealing a balance lasts while moving around the app but not across a reload.
 */
import { Injectable, signal } from '@angular/core';

export type Balance = 'total' | 'cash' | 'holdings';

@Injectable({
  providedIn: 'root'
})
export class BalanceVisibilityService {
  private readonly hiddenBalances = signal<ReadonlySet<Balance>>(new Set(['total', 'cash', 'holdings']));

  isHidden(balance: Balance): boolean {
    return this.hiddenBalances().has(balance);
  }

  toggle(balance: Balance): void {
    this.hiddenBalances.update(hidden => {
      const next = new Set(hidden);
      if (!next.delete(balance)) {
        next.add(balance);
      }
      return next;
    });
  }

  hideAll(): void {
    this.hiddenBalances.set(new Set(['total', 'cash', 'holdings']));
  }
}
