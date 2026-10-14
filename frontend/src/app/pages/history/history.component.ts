import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { catchError, combineLatest, forkJoin, interval, map, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideBan,
  lucideCircleCheck,
  lucideCircleX,
  lucideChevronRight,
  lucideClock,
  lucideRefreshCw
} from '@ng-icons/lucide';
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
  OrderHistoryResult,
  OrderStatus
} from '../../services/history.service';
import { OrderEvent, OrderService } from '../../services/order.service';

/** Badge style and icon per order status, so a status reads the same everywhere on the page. */
const STATUS_META: Record<OrderStatus, {
  label: string;
  variant: 'default' | 'secondary' | 'destructive' | 'outline';
  icon: string;
}> = {
  ACCEPTED: { label: 'Accepted', variant: 'secondary', icon: 'lucideCircleCheck' },
  FAILED: { label: 'Failed', variant: 'outline', icon: 'lucideBan' },
  FILLED: { label: 'Filled', variant: 'default', icon: 'lucideCircleCheck' },
  SUBMITTED: { label: 'Submitted', variant: 'secondary', icon: 'lucideClock' },
  REJECTED: { label: 'Rejected', variant: 'destructive', icon: 'lucideCircleX' }
};

/** Trader blotter: every order and cash movement for the chosen period, refreshed automatically. */
@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports, HlmTabsImports
  ],
  providers: [provideIcons({
    lucideBan, lucideCircleCheck, lucideCircleX, lucideChevronRight, lucideClock, lucideRefreshCw
  })]
})
export class HistoryComponent {
  private readonly history = inject(HistoryService);
  private readonly orderService = inject(OrderService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly periods: { value: HistoryPeriod; label: string }[] = [
    { value: '1d', label: '1 day' },
    { value: '7d', label: '7 days' },
    { value: '1m', label: '1 month' },
    { value: '1y', label: '1 year' }
  ];
  protected readonly statuses: OrderStatus[] = ['SUBMITTED', 'ACCEPTED', 'FILLED', 'REJECTED', 'FAILED'];

  protected readonly tab = signal('orders');
  protected readonly period = signal<HistoryPeriod>('1m');
  protected readonly statusFilter = signal<OrderStatus | null>(null);

  protected readonly orders = signal<OrderHistoryResult[]>([]);
  protected readonly cash = signal<CashTransactionResult[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly lastUpdated = signal<Date | null>(null);

  /** The order whose timeline is open, if any. */
  protected readonly selectedOrderId = signal<number | null>(null);
  protected readonly events = signal<OrderEvent[]>([]);
  protected readonly eventsLoading = signal(false);
  protected readonly eventsError = signal('');

  protected readonly visibleOrders = computed(() => {
    const status = this.statusFilter();
    return status ? this.orders().filter(o => o.orderStatus === status) : this.orders();
  });

  protected readonly selectedOrder = computed(() =>
    this.orders().find(o => o.orderId === this.selectedOrderId()) ?? null);

  protected countFor(status: OrderStatus): number {
    return this.orders().filter(o => o.orderStatus === status).length;
  }

  protected statusMeta(status: string | undefined) {
    return STATUS_META[status as OrderStatus] ?? { label: status ?? '', variant: 'secondary', icon: 'lucideClock' };
  }

  /** Filled orders are valued at the price they actually executed at. */
  protected effectivePrice(o: OrderHistoryResult): number {
    return o.executionPrice ?? o.price;
  }

  constructor() {
    // Reload when the period changes, and every 15 seconds so status changes show without a refresh (BR-07)
    combineLatest([toObservable(this.period), interval(15_000).pipe(startWith(0))]).pipe(
      switchMap(([period]) => forkJoin({
        orders: this.orderService.getOrders(period),
        cash: this.history.getCashHistory(period)
      })),
      takeUntilDestroyed(this.destroyRef)
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

    // Load the open order's timeline, and reload it when the auto-refresh shows its status changed
    const selectedKey = computed(() => {
      const order = this.selectedOrder();
      return order ? `${order.orderId}:${order.orderStatus}` : null;
    });
    toObservable(selectedKey).pipe(
      switchMap(key => {
        if (!key) {
          return of(null);
        }
        this.eventsLoading.set(true);
        return this.orderService.getOrderEvents(Number(key.split(':')[0])).pipe(
          map(events => ({ events, failed: false })),
          catchError(() => of({ events: [] as OrderEvent[], failed: true }))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      this.events.set(result?.events ?? []);
      this.eventsError.set(result?.failed ? 'Could not load this order\'s timeline.' : '');
      this.eventsLoading.set(false);
    });
  }

  protected setPeriod(period: HistoryPeriod): void {
    if (period !== this.period()) {
      this.loading.set(true);
      this.period.set(period);
    }
  }

  protected toggleTimeline(orderId: number): void {
    // Clear the previous order's steps so they never show under a different order while loading
    this.events.set([]);
    this.eventsError.set('');
    this.selectedOrderId.update(current => (current === orderId ? null : orderId));
  }
}
