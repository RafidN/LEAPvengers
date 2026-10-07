import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { combineLatest, forkJoin, interval, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideBan, lucideCircleCheck, lucideCircleX, lucideClock, lucideRefreshCw } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { HlmTabsImports } from '@spartan-ng/helm/tabs';
import {
  CashTransactionResult,
  HistoryPeriod,
  HistoryService,
  OrderHistoryResult
} from '../../services/history.service';

type OrderStatus = 'Filled' | 'Pending' | 'Rejected' | 'Canceled';

/** Badge style and icon per order status, so a status reads the same everywhere on the page. */
const STATUS_STYLE: Record<OrderStatus, { variant: 'default' | 'secondary' | 'destructive' | 'outline'; icon: string }> = {
  Filled: { variant: 'default', icon: 'lucideCircleCheck' },
  Pending: { variant: 'secondary', icon: 'lucideClock' },
  Rejected: { variant: 'destructive', icon: 'lucideCircleX' },
  Canceled: { variant: 'outline', icon: 'lucideBan' }
};

/** Trader blotter: every order and cash movement for the chosen period, refreshed automatically. */
@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports, HlmTabsImports
  ],
  providers: [provideIcons({ lucideBan, lucideCircleCheck, lucideCircleX, lucideClock, lucideRefreshCw })]
})
export class HistoryComponent {
  private readonly history = inject(HistoryService);

  protected readonly periods: { value: HistoryPeriod; label: string }[] = [
    { value: '1d', label: '1 day' },
    { value: '7d', label: '7 days' },
    { value: '1m', label: '1 month' },
    { value: '1y', label: '1 year' }
  ];
  protected readonly statuses: OrderStatus[] = ['Filled', 'Pending', 'Rejected', 'Canceled'];

  protected readonly tab = signal('orders');
  protected readonly period = signal<HistoryPeriod>('1m');
  protected readonly statusFilter = signal<OrderStatus | null>(null);

  protected readonly orders = signal<OrderHistoryResult[]>([]);
  protected readonly cash = signal<CashTransactionResult[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly lastUpdated = signal<Date | null>(null);

  protected readonly visibleOrders = computed(() => {
    const status = this.statusFilter();
    return status ? this.orders().filter(o => o.orderStatus === status) : this.orders();
  });

  protected countFor(status: OrderStatus): number {
    return this.orders().filter(o => o.orderStatus === status).length;
  }

  protected statusStyle(status: string) {
    return STATUS_STYLE[status as OrderStatus] ?? STATUS_STYLE.Pending;
  }

  constructor() {
    // Reload when the period changes, and every 15 seconds so status changes show without a refresh (BR-07)
    combineLatest([toObservable(this.period), interval(15_000).pipe(startWith(0))]).pipe(
      switchMap(([period]) => forkJoin({
        orders: this.history.getOrderHistory(period),
        cash: this.history.getCashHistory(period)
      })),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe({
      next: ({ orders, cash }) => {
        this.orders.set(orders);
        this.cash.set(cash);
        this.loading.set(false);
        this.error.set('');
        this.lastUpdated.set(new Date());
      },
      error: () => {
        this.error.set('Could not load your history. Is the backend running?');
        this.loading.set(false);
      }
    });
  }

  protected setPeriod(period: HistoryPeriod): void {
    if (period !== this.period()) {
      this.loading.set(true);
      this.period.set(period);
    }
  }
}
