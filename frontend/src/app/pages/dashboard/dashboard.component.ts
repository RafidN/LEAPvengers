import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe, NgClass, NgTemplateOutlet, PercentPipe } from '@angular/common';
import { DisplayCurrencyPipe } from '../../shared/display-currency.pipe';
import { RouterLink } from '@angular/router';
import { catchError, combineLatest, forkJoin, interval, map, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowDown, lucideArrowDownRight, lucideArrowLeftRight, lucideArrowUp, lucideArrowUpRight, lucideBell, lucideEye, lucideEyeOff,
  lucideGripVertical, lucideLayoutDashboard, lucidePlus, lucideRotateCcw, lucideSettings2, lucideX
} from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { CashTransactionResult, HistoryService, OrderHistoryResult, OrderStatus } from '../../services/history.service';
import {
  AccountHolding, AccountPortfolio, AccountService, AccountValuePoint, ValuePeriod
} from '../../services/account.service';
import { Balance, BalanceVisibilityService } from '../../services/balance-visibility.service';
import { PRICE_REFRESH_MS } from '../../services/instrument.service';
import { ValueChartComponent } from '../../components/value-chart/value-chart.component';
import { MarketMoversComponent } from '../../components/market-movers/market-movers.component';
import { MarketNewsComponent } from '../../components/market-news/market-news.component';
import { InstrumentSearchWidgetComponent } from '../../components/instrument-search-widget/instrument-search-widget.component';
import { MarketMovers, MarketNewsArticle, MarketService } from '../../services/market.service';
import { formatMoney, formatQuantity } from '../../services/format';
import { DisplayCurrencyService } from '../../services/display-currency.service';
import { PreferencesService } from '../../services/preferences.service';

/**
 * One row of Recent activity. A filled order or a cash movement shows its amount on the right;
 * an order that didn't fill shows its status there instead.
 */
interface ActivityItem {
  key: string;
  date: string;
  title: string;           // e.g. "Bought 20 NVO", "Deposit"
  detail?: string;         // e.g. "at $43.00"
  amount?: number;
  /** Money in (green, +) or out (red, −); undefined shows the amount without a sign. */
  direction?: 'in' | 'out';
  status?: { label: string; variant: 'secondary' | 'destructive' | 'outline' };
}

type DashboardSection = 'total' | 'cash' | 'holdingsValue' | 'value' | 'holdings' | 'activity' | 'movers' | 'news' | 'search';

const DEFAULT_SECTIONS: DashboardSection[] = ['total', 'cash', 'holdingsValue', 'value', 'holdings', 'activity', 'movers', 'news', 'search'];
const CORE_SECTIONS: DashboardSection[] = ['total', 'cash', 'holdingsValue', 'value', 'holdings', 'activity'];
const DASHBOARD_LAYOUT_KEY = 'dashboard-layout-v2';

/** Badges for orders that haven't filled; filled orders show their value instead. */
const UNFILLED_STATUS: Partial<Record<OrderStatus, ActivityItem['status']>> = {
  SUBMITTED: { label: 'In progress', variant: 'secondary' },
  ACCEPTED: { label: 'In progress', variant: 'secondary' },
  REJECTED: { label: 'Rejected', variant: 'destructive' },
  FAILED: { label: 'Failed', variant: 'outline' }
};

/** A holding with the account it is in, since a trader can have several accounts. */
interface HoldingRow extends AccountHolding {
  accountId: number;
}

interface MarketAlert {
  ticker: string;
  type: 'mover' | 'news';
  title: string;
  detail: string;
  link?: string;
}

/**
 * The whole value history is rebuilt on the server, so it is re-read less often than prices; in between,
 * the chart's last point follows the live totals.
 */
const HISTORY_REFRESH_MS = 30_000;

const holdingKey = (accountId: number, ticker: string) => `${accountId}:${ticker}`;

function isToday(iso: string): boolean {
  const date = new Date(iso);
  const now = new Date();
  return Number.isFinite(date.getTime())
    && date.getFullYear() === now.getFullYear()
    && date.getMonth() === now.getMonth()
    && date.getDate() === now.getDate();
}

function newsMentionsHolding(text: string, holding: HoldingRow): boolean {
  const normalized = text.toUpperCase();
  const ticker = holding.ticker.toUpperCase();
  if (normalized.includes(ticker) || normalized.includes(holding.instrumentName.toUpperCase())) {
    return true;
  }
  const nameWords = holding.instrumentName.toUpperCase()
    .split(/[^A-Z0-9]+/)
    .filter(word => word.length >= 4 && !['LTD', 'INC', 'PLC', 'CORP', 'LIMITED'].includes(word));
  return nameWords.some(word => normalized.includes(word));
}

/**
 * Trader home page: account totals, account value over time, holdings and recent activity.
 * Totals, holdings and the end of the chart refresh every 3 seconds, as often as prices change.
 * Each total has an eye button; all start hidden (see BalanceVisibilityService).
 */
@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  imports: [
    DisplayCurrencyPipe, DatePipe, DecimalPipe, NgClass, NgTemplateOutlet, PercentPipe, RouterLink, NgIcon, ValueChartComponent,
    MarketMoversComponent, MarketNewsComponent, InstrumentSearchWidgetComponent,
    CdkDrag, CdkDragHandle, CdkDropList, HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports
  ],
  providers: [provideIcons({
    lucideArrowDown, lucideArrowDownRight, lucideArrowLeftRight, lucideArrowUp, lucideArrowUpRight, lucideBell, lucideEye, lucideEyeOff,
    lucideGripVertical, lucideLayoutDashboard, lucidePlus, lucideRotateCcw, lucideSettings2, lucideX
  })]
})
export class DashboardComponent {
  private readonly history = inject(HistoryService);
  private readonly accountService = inject(AccountService);
  private readonly marketService = inject(MarketService);
  private readonly visibility = inject(BalanceVisibilityService);
  protected readonly preferences = inject(PreferencesService);
  private readonly currency = inject(DisplayCurrencyService);

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly lastUpdated = signal<Date | null>(null);

  protected readonly portfolios = signal<AccountPortfolio[]>([]);
  protected readonly cashTransactions = signal<CashTransactionResult[]>([]);
  protected readonly orders = signal<OrderHistoryResult[]>([]);
  /** Which way each holding's price last moved, for the up/down arrow. */
  protected readonly priceMoves = signal<Record<string, 'up' | 'down'>>({});

  protected readonly valuePeriods: { value: ValuePeriod; label: string }[] = [
    { value: '1d', label: '1D' },
    { value: '7d', label: '7D' },
    { value: '1m', label: '1M' },
    { value: '1y', label: '1Y' }
  ];
  protected readonly valuePeriod = signal<ValuePeriod>('1m');
  protected readonly valuePoints = signal<AccountValuePoint[]>([]);
  protected readonly valueLoading = signal(true);
  protected readonly valueError = signal(false);
  protected readonly editLayout = signal(false);
  protected readonly widgetMenuOpen = signal(false);
  protected readonly sectionOrder = signal<DashboardSection[]>(this.loadSectionOrder());
  protected readonly hiddenSections = signal<DashboardSection[]>(this.loadHiddenSections());
  protected readonly marketNews = signal<MarketNewsArticle[]>([]);
  protected readonly marketMovers = signal<MarketMovers | null>(null);
  protected readonly availableWidgets = computed(() => DEFAULT_SECTIONS.filter(section =>
    !this.sectionOrder().includes(section) || this.hiddenSections().includes(section)));

  protected readonly marketAlerts = computed<MarketAlert[]>(() => {
    if (!this.preferences.newsAlerts()) {
      return [];
    }
    const holdings = this.holdings();
    const alerts: MarketAlert[] = [];
    const heldTickers = new Set(holdings.map(holding => holding.ticker.toUpperCase()));

    const movers = this.marketMovers();
    if (movers && isToday(movers.asOf)) {
      for (const mover of [...movers.gainers, ...movers.losers]) {
        if (heldTickers.has(mover.ticker.toUpperCase())) {
          alerts.push({
            ticker: mover.ticker,
            type: 'mover',
            title: `${mover.ticker} is a top mover today`,
            detail: `${mover.changePercent >= 0 ? '+' : '−'}${Math.abs(mover.changePercent).toFixed(2)}% today`,
            link: `/markets?ticker=${encodeURIComponent(mover.ticker)}`
          });
        }
      }
    }

    for (const article of this.marketNews()) {
      if (!article.publishedAt || !isToday(article.publishedAt)) {
        continue;
      }
      const text = `${article.title} ${article.summary ?? ''}`.toUpperCase();
      const holding = holdings.find(item => newsMentionsHolding(text, item));
      if (holding) {
        alerts.push({
          ticker: holding.ticker,
          type: 'news',
          title: `${holding.ticker} appears in today's news`,
          detail: article.title,
          link: article.link
        });
      }
    }
    return alerts.slice(0, 5);
  });

  protected readonly holdings = computed<HoldingRow[]>(() =>
    this.portfolios().flatMap(p => p.holdings.map(h => ({ ...h, accountId: p.accountId }))));

  /** The account balance already includes filled trades. */
  protected readonly cash = computed(() => this.portfolios().reduce((total, p) => total + p.balance, 0));

  protected readonly holdingsValue = computed(() => this.holdings().reduce((total, h) => total + h.value, 0));

  protected readonly totalValue = computed(() => this.cash() + this.holdingsValue());

  /** The three cards at the top, each with its own eye button. */
  protected readonly totals = computed<{ key: Balance; label: string; value: number }[]>(() => [
    { key: 'total', label: 'Total value', value: this.totalValue() },
    { key: 'cash', label: 'Cash available', value: this.cash() },
    { key: 'holdings', label: 'Holdings value', value: this.holdingsValue() }
  ]);

  /**
   * The value history with its last point ("now") replaced by the live totals, so the end of the line
   * moves with every price refresh between the slower history refreshes.
   */
  protected readonly chartPoints = computed<AccountValuePoint[]>(() => {
    const points = this.valuePoints();
    const updated = this.lastUpdated();
    if (points.length < 2 || !updated || !this.portfolios().length) {
      return points;
    }
    return [...points.slice(0, -1), {
      time: updated.toISOString(),
      totalValue: this.totalValue(),
      cash: this.cash(),
      holdingsValue: this.holdingsValue()
    }];
  });

  /** Change in total value from the first to the last point of the period. */
  protected readonly valueChange = computed(() => {
    const points = this.chartPoints();
    if (points.length < 2) {
      return null;
    }
    const first = points[0].totalValue;
    const last = points[points.length - 1].totalValue;
    return { amount: last - first, ratio: first ? (last - first) / first : 0 };
  });

  protected readonly periodPhrase = computed(() => ({
    '1d': 'today', '7d': 'over the past week', '1m': 'over the past month', '1y': 'over the past year'
  })[this.valuePeriod()]);

  protected readonly activity = computed<ActivityItem[]>(() => [
    // Trades already appear below as orders, so only deposits and withdrawals come from the cash history
    ...this.cashTransactions()
      .filter(t => t.transactionType === 'DEPOSIT' || t.transactionType === 'WITHDRAWAL')
      .map((t): ActivityItem => ({
        key: `cash-${t.cashTransactionId}`,
        date: t.transactionTime ?? t.transactionDate,
        title: t.transactionType === 'DEPOSIT' ? 'Deposit' : 'Withdrawal',
        amount: t.amount,
        direction: t.transactionType === 'DEPOSIT' ? 'in' : 'out'
      })),
    ...this.orders().map((o): ActivityItem => {
      const filled = o.orderStatus === 'FILLED';
      const price = o.executionPrice ?? o.price;
      // Only a filled order actually bought or sold anything
      const verb = o.orderType === 'SELL' ? (filled ? 'Sold' : 'Sell') : (filled ? 'Bought' : 'Buy');
      return {
        key: `order-${o.orderId}`,
        date: o.submittedAt ?? o.orderDate,
        title: `${verb} ${formatQuantity(o.quantity)} ${o.ticker}`,
        detail: `at ${this.currency.format(price)}`,
        amount: filled ? o.quantity * price : undefined,
        status: filled ? undefined : UNFILLED_STATUS[o.orderStatus]
      };
    })
  ].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 6));

  protected isSectionVisible(section: DashboardSection): boolean {
    return !this.hiddenSections().includes(section);
  }

  protected sectionLabel(section: DashboardSection): string {
    return {
      total: 'Total value', cash: 'Cash balance', holdingsValue: 'Holdings value', value: 'Account value',
      holdings: 'Holdings', activity: 'Recent activity', movers: 'Top movers', news: 'Market news', search: 'Instrument search'
    }[section];
  }

  protected widgetClass(section: DashboardSection): string {
    switch (section) {
      case 'total':
      case 'cash':
      case 'holdingsValue':
        return 'md:col-span-1';
      case 'holdings':
        return 'md:col-span-2';
      case 'activity':
        return 'md:col-span-1';
      case 'value':
      case 'movers':
      case 'news':
      case 'search':
      default:
        return 'md:col-span-3';
    }
  }

  protected dropSection(event: CdkDragDrop<DashboardSection[]>): void {
    if (event.previousIndex === event.currentIndex) {
      return;
    }
    const order = [...this.sectionOrder()];
    moveItemInArray(order, event.previousIndex, event.currentIndex);
    this.sectionOrder.set(order);
    this.saveLayout();
  }

  protected removeSection(section: DashboardSection): void {
    this.hiddenSections.update(hidden => hidden.includes(section) ? hidden : [...hidden, section]);
    this.saveLayout();
  }

  protected resetLayout(): void {
    this.sectionOrder.set([...CORE_SECTIONS]);
    this.hiddenSections.set([]);
    this.widgetMenuOpen.set(false);
    this.saveLayout();
  }

  protected addWidget(section: DashboardSection): void {
    if (!DEFAULT_SECTIONS.includes(section)) {
      return;
    }
    this.hiddenSections.update(hidden => hidden.filter(item => item !== section));
    this.sectionOrder.update(order => order.includes(section) ? order : [...order, section]);
    this.widgetMenuOpen.set(false);
    this.saveLayout();
  }

  private loadSectionOrder(): DashboardSection[] {
    const saved = this.readLayout()?.order;
    if (!saved || !Array.isArray(saved)) {
      return [...CORE_SECTIONS];
    }
    const valid = saved.filter((section, index) => DEFAULT_SECTIONS.includes(section) && saved.indexOf(section) === index);
    return [...valid, ...CORE_SECTIONS.filter(section => !valid.includes(section))];
  }

  private loadHiddenSections(): DashboardSection[] {
    const saved = this.readLayout()?.hidden;
    return saved?.filter(section => DEFAULT_SECTIONS.includes(section)) ?? [];
  }

  private readLayout(): { order: DashboardSection[]; hidden: DashboardSection[] } | null {
    if (typeof localStorage === 'undefined') {
      return null;
    }
    try {
      const parsed = JSON.parse(localStorage.getItem(DASHBOARD_LAYOUT_KEY) ?? 'null');
      return parsed?.order && Array.isArray(parsed.hidden) ? parsed : null;
    } catch {
      return null;
    }
  }

  private saveLayout(): void {
    if (typeof localStorage === 'undefined') {
      return;
    }
    try {
      localStorage.setItem(DASHBOARD_LAYOUT_KEY, JSON.stringify({ order: this.sectionOrder(), hidden: this.hiddenSections() }));
    } catch {
      // Preferences are optional; the layout still works for this visit.
    }
  }

  constructor() {
    const destroyRef = inject(DestroyRef);
    const priceTick$ = interval(PRICE_REFRESH_MS).pipe(startWith(0));
    const historyTick$ = interval(HISTORY_REFRESH_MS).pipe(startWith(0));
    const marketTick$ = interval(60_000).pipe(startWith(0));

    marketTick$.pipe(
      switchMap(() => forkJoin({
        news: this.marketService.getNews().pipe(catchError(() => of([] as MarketNewsArticle[]))),
        movers: this.marketService.getMovers().pipe(catchError(() => of(null)))
      })),
      takeUntilDestroyed(destroyRef)
    ).subscribe(({ news, movers }) => {
      this.marketNews.set(news);
      this.marketMovers.set(movers);
    });

    forkJoin({
      cash: this.history.getCashHistory('1y'),
      orders: this.history.getOrderHistory('1m')
    }).subscribe({
      next: ({ cash, orders }) => {
        this.cashTransactions.set(cash);
        this.orders.set(orders);
      },
      error: () => {} // Recent activity just stays empty; the totals report a backend that is down
    });

    // Cash and holdings at the latest prices, for every account, every time prices can have changed
    priceTick$.pipe(
      switchMap(() => this.accountService.getAccounts().pipe(
        switchMap(accounts => accounts.length
          ? forkJoin(accounts.map(a => this.accountService.getPortfolio(a.accountId)))
          : of([] as AccountPortfolio[])),
        catchError(() => of(null))
      )),
      takeUntilDestroyed(destroyRef)
    ).subscribe(portfolios => {
      this.loading.set(false);
      if (!portfolios) {
        this.error.set('Could not load your account. Is the backend running?');
        return;
      }
      this.error.set('');
      this.trackPriceMoves(portfolios);
      this.portfolios.set(portfolios);
      this.lastUpdated.set(new Date());
    });

    // Account value over the chosen period, re-read now and then; chartPoints keeps its end live in between
    let shownPeriod: ValuePeriod | null = null;
    combineLatest([toObservable(this.valuePeriod), historyTick$]).pipe(
      switchMap(([period]) => {
        // Only a new period shows the loading state; refreshes swap the line in place
        if (period !== shownPeriod) {
          this.valueLoading.set(true);
          this.valuePoints.set([]);
        }
        return this.accountService.getValueHistory(period).pipe(
          catchError(() => of(null)),
          map(points => ({ period, points }))
        );
      }),
      takeUntilDestroyed(destroyRef)
    ).subscribe(({ period, points }) => {
      shownPeriod = period;
      if (points) {
        this.valuePoints.set(points);
      }
      this.valueError.set(points === null && this.valuePoints().length === 0);
      this.valueLoading.set(false);
    });
  }

  protected isHidden(balance: Balance): boolean {
    return this.visibility.isHidden(balance);
  }

  protected toggle(balance: Balance): void {
    this.visibility.toggle(balance);
  }

  protected rowKey(h: HoldingRow): string {
    return holdingKey(h.accountId, h.ticker);
  }

  /** Compares new prices with the ones on screen; a price that didn't move keeps its last arrow. */
  private trackPriceMoves(portfolios: AccountPortfolio[]): void {
    const previous = new Map(this.holdings().map(h => [this.rowKey(h), h.latestPrice]));
    const moves = { ...this.priceMoves() };
    for (const p of portfolios) {
      for (const h of p.holdings) {
        const key = holdingKey(p.accountId, h.ticker);
        const before = previous.get(key);
        if (before != null && h.latestPrice !== before) {
          moves[key] = h.latestPrice > before ? 'up' : 'down';
        }
      }
    }
    this.priceMoves.set(moves);
  }
}
