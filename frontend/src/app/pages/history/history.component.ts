import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DOCUMENT, DatePipe, DecimalPipe } from '@angular/common';
import { DisplayCurrencyPipe } from '../../shared/display-currency.pipe';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, defer, forkJoin, map, of, repeat, switchMap, timer } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowLeftRight,
  lucideBan,
  lucideCircleCheck,
  lucideCircleX,
  lucideChevronRight,
  lucideClock,
  lucideFlag,
  lucideMessageSquareReply,
  lucideRefreshCw
} from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { HlmTabsImports } from '@spartan-ng/helm/tabs';
import {
  CashTransactionResult,
  HistoryPeriod,
  HistoryService,
  OrderHistoryResult,
  OrderStatus
} from '../../services/history.service';
import {
  DISPUTE_REASONS, Dispute, DisputeReason, FINAL_STATUSES, OrderEvent, OrderService, disputeReasonLabel
} from '../../services/order.service';

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

/** Label per cash transaction type, and whether it brings money into the account. */
const CASH_META: Record<string, { label: string; incoming: boolean }> = {
  DEPOSIT: { label: 'Deposit', incoming: true },
  WITHDRAWAL: { label: 'Withdrawal', incoming: false },
  BUY: { label: 'Buy', incoming: false },
  SELL: { label: 'Sell', incoming: true }
};

/** Reload every 15 seconds, or every second while an order is still on its way to FILLED, REJECTED or FAILED. */
const REFRESH_MS = 15_000;
const IN_PROGRESS_REFRESH_MS = 1_000;

/**
 * Trader blotter: every order and cash movement for the chosen period, refreshed automatically.
 * /history?order=<id> opens that order's timeline, e.g. when coming from the trade page.
 */
@Component({
  selector: 'app-history',
  templateUrl: './history.component.html',
  imports: [
    DisplayCurrencyPipe, DatePipe, DecimalPipe, FormsModule, NgIcon, RouterLink,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports,
    HlmTableImports, HlmTabsImports
  ],
  providers: [provideIcons({
    lucideArrowLeftRight, lucideBan, lucideCircleCheck, lucideCircleX, lucideChevronRight, lucideClock, lucideFlag,
    lucideMessageSquareReply, lucideRefreshCw
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

  // ---- Disputes: a client reports a problem with an order; Operations replies ----
  protected readonly disputeReasons = DISPUTE_REASONS;
  protected readonly reasonLabel = disputeReasonLabel;
  protected readonly disputes = signal<Dispute[]>([]);
  /** The order whose dispute form is open, if any. */
  protected readonly disputeFormFor = signal<number | null>(null);
  protected readonly disputeReason = signal<DisputeReason | ''>('');
  protected readonly disputeMessage = signal('');
  protected readonly disputeSending = signal(false);
  protected readonly disputeError = signal('');
  protected readonly maxDisputeLength = 1000;

  /** Each order's most recent dispute; the list comes newest first. */
  protected readonly latestDispute = computed(() => {
    const latest = new Map<number, Dispute>();
    for (const d of this.disputes()) {
      if (!latest.has(d.orderId)) {
        latest.set(d.orderId, d);
      }
    }
    return latest;
  });

  protected readonly visibleOrders = computed(() => {
    const status = this.statusFilter();
    return status ? this.orders().filter(o => o.orderStatus === status) : this.orders();
  });

  protected readonly selectedOrder = computed(() =>
    this.orders().find(o => o.orderId === this.selectedOrderId()) ?? null);

  /** True while some order is still SUBMITTED or ACCEPTED, so the page refreshes faster. */
  protected readonly inProgress = computed(() =>
    this.orders().some(o => o.orderStatus === 'SUBMITTED' || o.orderStatus === 'ACCEPTED'));

  /** The order opened from the URL, highlighted until another is opened. */
  protected readonly linkedOrderId = signal<number | null>(null);

  protected countFor(status: OrderStatus): number {
    return this.orders().filter(o => o.orderStatus === status).length;
  }

  protected cashMeta(type: string) {
    return CASH_META[type] ?? { label: type, incoming: false };
  }

  protected statusMeta(status: string | undefined) {
    return STATUS_META[status as OrderStatus] ?? { label: status ?? '', variant: 'secondary', icon: 'lucideClock' };
  }

  /** Filled orders are valued at the price they actually executed at. */
  protected effectivePrice(o: OrderHistoryResult): number {
    return o.executionPrice ?? o.price;
  }

  constructor() {
    const document = inject(DOCUMENT);
    const linked = Number(inject(ActivatedRoute).snapshot.queryParamMap.get('order'));
    let scrollToLinked = Number.isInteger(linked) && linked > 0;
    if (scrollToLinked) {
      this.selectedOrderId.set(linked);
      this.linkedOrderId.set(linked);
    }

    // Reload when the period changes, then again after each load so status changes show without a refresh (BR-07)
    toObservable(this.period).pipe(
      switchMap(period => defer(() => forkJoin({
        orders: this.orderService.getOrders(period),
        cash: this.history.getCashHistory(period),
        // Disputes are extra: if they can't load, the orders still show
        disputes: this.orderService.getDisputes().pipe(catchError(() => of(null)))
      }).pipe(catchError(() => of(null)))).pipe(
        repeat({ delay: () => timer(this.inProgress() ? IN_PROGRESS_REFRESH_MS : REFRESH_MS) })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      this.loading.set(false);
      if (!result) {
        this.error.set('Could not load your history. Is the backend running?');
        return;
      }
      this.orders.set(result.orders);
      this.cash.set(result.cash);
      if (result.disputes) {
        this.disputes.set(result.disputes);
      }
      this.error.set('');
      this.lastUpdated.set(new Date());

      if (scrollToLinked) {
        scrollToLinked = false;
        // Wait a tick for the row to render
        setTimeout(() => document.getElementById(`order-row-${linked}`)?.scrollIntoView({ block: 'center', behavior: 'smooth' }));
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

  /** Only a finished order can be disputed; one still going through has no outcome yet. */
  protected canDispute(o: OrderHistoryResult): boolean {
    return FINAL_STATUSES.includes(o.orderStatus) && this.latestDispute().get(o.orderId)?.status !== 'OPEN';
  }

  protected openDisputeForm(orderId: number): void {
    this.disputeFormFor.set(orderId);
    this.disputeReason.set('');
    this.disputeMessage.set('');
    this.disputeError.set('');
  }

  protected closeDisputeForm(): void {
    this.disputeFormFor.set(null);
  }

  protected submitDispute(orderId: number): void {
    const reason = this.disputeReason();
    const message = this.disputeMessage().trim();
    if (!reason) {
      this.disputeError.set('Choose what went wrong.');
      return;
    }
    if (!message) {
      this.disputeError.set('Tell us what happened, so Operations can look into it.');
      return;
    }
    this.disputeSending.set(true);
    this.disputeError.set('');
    this.orderService.raiseDispute(orderId, reason, message).subscribe({
      next: dispute => {
        let audio = new Audio();
        audio.src = "ordersuccess.mp3";
        audio.load();
        audio.play();
        this.disputeSending.set(false);
        this.disputes.update(list => [dispute, ...list]);
        this.disputeFormFor.set(null);
      },
      error: (error: HttpErrorResponse) => {
        let audio = new Audio();
        audio.src = "orderfailure.mp3";
        audio.load();
        audio.play();
        this.disputeSending.set(false);
        this.disputeError.set(error.status === 400 && error.error?.message
          ? error.error.message
          : 'Could not send your dispute. Please try again.');
      }
    });
  }

  protected toggleTimeline(orderId: number): void {
    // Clear the previous order's steps so they never show under a different order while loading
    this.events.set([]);
    this.eventsError.set('');
    this.linkedOrderId.set(null);
    this.disputeFormFor.set(null);
    this.selectedOrderId.update(current => (current === orderId ? null : orderId));
  }
}
