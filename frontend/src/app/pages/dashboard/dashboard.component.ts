import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, interval, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideEye, lucideEyeOff } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import {
  CashTransactionResult,
  HistoryService,
  OrderHistoryResult,
  PortfolioHistoryResult
} from '../../services/history.service';
import { InstrumentSearchService, PriceQuoteResult, WATCHLIST } from '../../services/instrument-search.service';
import { AccountResponse, AccountService } from '../../services/account.service';

interface ActivityItem {
  date: string;
  text: string;
  kind: 'cash' | 'order';
}

/** Trader home page: account totals, holdings, recent activity and a live watchlist. */
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, RouterLink, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports
  ],
  providers: [provideIcons({ lucideEye, lucideEyeOff })]
})
export class DashboardComponent {
  private readonly history = inject(HistoryService);
  private readonly instruments = inject(InstrumentSearchService);
  private readonly accountService = inject(AccountService);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly balanceHidden = signal(false);

  protected readonly accounts = signal<AccountResponse[]>([]);
  protected readonly cashTransactions = signal<CashTransactionResult[]>([]);
  protected readonly holdings = signal<PortfolioHistoryResult[]>([]);
  protected readonly orders = signal<OrderHistoryResult[]>([]);
  protected readonly watchlist = signal<PriceQuoteResult[]>([]);

  // The account balance already includes filled trades, unlike the deposit/withdrawal history
  protected readonly cash = computed(() =>
    this.accounts().reduce((total, a) => total + a.balance, 0));

  protected readonly holdingsValue = computed(() =>
    this.holdings().reduce((total, h) => total + h.totalValue, 0));

  protected readonly totalValue = computed(() => this.cash() + this.holdingsValue());

  protected readonly activity = computed<ActivityItem[]>(() => [
    ...this.cashTransactions().map(t => ({
      date: t.transactionDate,
      text: `${t.transactionType === 'WITHDRAWAL' ? 'Withdrew' : 'Deposited'} $${t.amount.toLocaleString()}`,
      kind: 'cash' as const
    })),
    ...this.orders().map(o => ({
      date: o.submittedAt ?? o.orderDate,
      text: `${o.orderType === 'SELL' ? 'Sold' : 'Bought'} ${o.quantity} ${o.ticker} at ${o.price} (${o.orderStatus})`,
      kind: 'order' as const
    }))
  ].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 6));

  constructor() {
    forkJoin({
      accounts: this.accountService.getAccounts(),
      cash: this.history.getCashHistory('past-year'),
      // 'today' would only list positions that changed today; past-year returns every current position
      holdings: this.history.getPortfolioHistory('past-year'),
      orders: this.history.getOrderHistory('past-month')
    }).subscribe({
      next: ({ accounts, cash, holdings, orders }) => {
        this.accounts.set(accounts);
        this.cashTransactions.set(cash);
        this.holdings.set(holdings);
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load your account. Is the backend running?');
        this.loading.set(false);
      }
    });

    // The live quote generator adds a price every 15 seconds
    interval(15_000).pipe(
      startWith(0),
      switchMap(() => this.instruments.getQuotes(WATCHLIST)),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(quotes => this.watchlist.set(quotes));
  }

  protected toggleBalance(): void {
    this.balanceHidden.update(hidden => !hidden);
  }
}
