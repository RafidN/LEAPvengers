import { Component, DestroyRef, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  Subject, catchError, combineLatest, filter, forkJoin, interval, map, merge, of, startWith, switchMap, take, takeWhile,
  timer
} from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowDown, lucideArrowLeftRight, lucideArrowUp, lucideBan, lucideChartCandlestick, lucideCircleCheck,
  lucideCircleSlash, lucideCircleX, lucideClock, lucideChevronDown, lucideChevronUp, lucideHistory, lucideHourglass, lucideLoaderCircle, lucideMousePointerClick,
  lucidePanelRightClose, lucidePanelRightOpen, lucideRotateCcw, lucideTriangleAlert, lucideX
} from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmComboboxImports } from '@spartan-ng/helm/combobox';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { PricePoint } from '../../components/price-chart/price-chart.component';
import { ChartDraft, ChartOrder, TradingChartComponent } from '../../components/trading-chart/trading-chart.component';
import { OrderKind as ChartOrderKind, orderName } from '../../components/trading-chart/chart-geometry';
import { AccountPortfolio, AccountResponse, AccountService } from '../../services/account.service';
import { HistoryPeriod, HistoryService, OrderHistoryResult, OrderKind, OrderStatus } from '../../services/history.service';
import { Instrument, InstrumentService, PRICE_REFRESH_MS, Quote } from '../../services/instrument.service';
import { formatMoney, formatQuantity } from '../../services/format';
import {
  FINAL_STATUSES, OrderEvent, OrderRejection, OrderRequest, OrderService, OrderSide
} from '../../services/order.service';

/** How the instrument picker groups instruments, in display order. */
const INSTRUMENT_GROUPS: { label: string; matches: (i: Instrument) => boolean }[] = [
  { label: 'US & UK stocks', matches: i => i.assetClass === 'Equity' && i.market !== 'IN' },
  { label: 'Indian stocks', matches: i => i.assetClass === 'Equity' && i.market === 'IN' },
  { label: 'Crypto', matches: i => i.assetClass === 'Crypto' },
  { label: 'FX', matches: i => i.assetClass === 'FX' }
];

/** Plain-words headlines for the backend's rule messages (see TradingRules.java). */
const REASON_HEADLINES: { pattern: RegExp; headline: string }[] = [
  { pattern: /only has/i, headline: 'Not enough cash in this account' },
  { pattern: /cannot sell/i, headline: 'You don\'t hold enough of this to sell' },
  { pattern: /price moved|past your limit/i, headline: 'The price changed before your order could fill' },
  { pattern: /older than/i, headline: 'There is no current price to trade on' },
  { pattern: /is closed/i, headline: 'This market is closed right now' },
  { pattern: /suspended/i, headline: 'Trading in this instrument is paused' },
  { pattern: /restarted|stopped before/i, headline: 'The order was interrupted by a platform restart' },
  { pattern: /no price/i, headline: 'This instrument has no price yet' },
  { pattern: /greater than 0/i, headline: 'Quantity must be more than zero' },
  { pattern: /waits for the price/i, headline: 'The market is already at that price' },
  { pattern: /canceled automatically/i, headline: 'Canceled: the shares were sold by another order' },
  { pattern: /canceled by you/i, headline: 'You canceled this order' },
  { pattern: /replaced by/i, headline: 'Moved to a new price' }
];

type StepState = 'done' | 'current' | 'failed' | 'skipped';

/** One row of the order tracker: Submitted, Checked (or Working), Filled. */
interface TrackerStep {
  title: string;
  detail: string;
  state: StepState;
  time?: string;
}

/** A message about working orders: one filled, moved or canceled. */
interface WorkingNotice {
  tone: 'ok' | 'error';
  text: string;
}

/**
 * A quote younger than this (plus the market's data delay) counts as live; while a market is open the quote
 * generator writes its latest trade price every 3 seconds.
 */
const LIVE_QUOTE_MS = 2 * 60_000;

/** How often the tracker checks an order on its way to FILLED, REJECTED or FAILED. */
const TRACK_POLL_MS = 300;

/** Quantities are entered and shown with at most 2 decimal places, like every number on the site. */
const QUANTITY_DECIMALS = 2;
const isPresent = <T>(value: T | null): value is T => value != null;
const roundDown = (value: number) => Math.floor(value * 10 ** QUANTITY_DECIMALS) / 10 ** QUANTITY_DECIMALS;

/** A starting limit or stop price a little way from the market, on the side where the order waits. */
const STARTING_DISTANCE = { LIMIT: 0.01, STOP: 0.02 };
/** Prices under $10 (FX, small stocks) to 4 decimal places, others to the cent. */
const roundPrice = (price: number) => Number(price.toFixed(price < 10 ? 4 : 2));

/** Which tab of the drawer under the chart is showing. */
type BottomTab = 'working' | 'status' | 'today';

/** Wide enough for the order panel to sit beside the chart; narrower, it opens over it. */
const isWide = () => typeof window === 'undefined' || window.matchMedia('(min-width: 1024px)').matches;

/**
 * The Trade page, in two views (data.view on the route):
 *
 * Simple (/trade, the default): a plain ticket for beginners to buy or sell at the live price, with the order
 * tracker and today's orders beside it. Limit and stop orders placed elsewhere are listed but managed in advanced.
 *
 * Advanced (/trade/advanced): a full-window trading screen. The chart fills the page, the order ticket is a panel on the right (over the chart
 * on small screens) and working orders, the order tracker and today's orders are tabs in a drawer under the chart.
 * A market order fills at the live price; a limit or stop order waits as WORKING until the price reaches it. Limit
 * and stop orders can be set up in the ticket or on the chart, where working orders are lines that can be dragged to
 * a new price or canceled. The tracker follows the latest order through Submitted, Accepted or Working, and Filled
 * (or Rejected / Failed / Canceled).
 */
@Component({
  selector: 'app-trade',
  templateUrl: './trade.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, NgTemplateOutlet, TitleCasePipe, FormsModule, RouterLink, NgIcon,
    TradingChartComponent, HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmComboboxImports,
    HlmInputImports, HlmLabelImports
  ],
  providers: [provideIcons({
    lucideArrowDown, lucideArrowLeftRight, lucideArrowUp, lucideBan, lucideChartCandlestick, lucideChevronDown,
    lucideChevronUp, lucideCircleCheck, lucideCircleSlash, lucideCircleX, lucideClock, lucideHistory, lucideHourglass, lucideLoaderCircle, lucideMousePointerClick,
    lucidePanelRightClose, lucidePanelRightOpen, lucideRotateCcw, lucideTriangleAlert, lucideX
  })]
})
export class TradeComponent {
  private readonly accountService = inject(AccountService);
  private readonly instrumentService = inject(InstrumentService);
  private readonly orderService = inject(OrderService);
  private readonly historyService = inject(HistoryService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly quantityInput = viewChild<ElementRef<HTMLInputElement>>('quantityInput');

  /** The full-window chart with limit and stop orders, rather than the simple market-order ticket. */
  protected readonly advanced = this.route.snapshot.data['view'] === 'advanced';

  // ---- Order ticket ----
  protected readonly accounts = signal<AccountResponse[]>([]);
  protected readonly accountId = signal<number | null>(null);
  protected readonly portfolio = signal<AccountPortfolio | null>(null);

  protected readonly instruments = signal<Instrument[]>([]);
  protected readonly instrumentId = signal<number | null>(null);
  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoteError = signal('');
  /** False when the latest price is old, e.g. outside market hours or with the quote generator stopped. */
  protected readonly quoteIsLive = signal(false);
  /** Why the selected instrument can't be traded right now (its market is closed or it is suspended), if so. */
  protected readonly notTradable = computed(() => {
    const quote = this.quote();
    return quote?.tradable === false ? quote.notTradableReason ?? "This instrument can't be traded right now." : '';
  });
  /** Suspended by the trading desk, as opposed to just closed: then not even a limit or stop order can wait. */
  protected readonly suspended = computed(() => !!this.notTradable() && this.quote()?.marketOpen !== false);
  /** Which way the price moved at the last refresh, for the up/down arrow. */
  protected readonly priceMove = signal<'up' | 'down' | null>(null);

  protected readonly side = signal<OrderSide>('BUY');
  protected readonly kind = signal<OrderKind>('MARKET');
  protected readonly quantity = signal<number | null>(null);
  /** The limit or stop price, for LIMIT and STOP orders. */
  protected readonly triggerPrice = signal<number | null>(null);

  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly submitting = signal(false);
  /** A problem with the request itself, when no order was recorded. */
  protected readonly submitError = signal<{ headline: string; detail: string } | null>(null);

  // ---- Layout ----
  /** The order ticket panel; open beside the chart on wide screens, closed (over the chart when opened) on small ones. */
  protected readonly panelOpen = signal(isWide());
  protected readonly bottomOpen = signal(true);
  protected readonly bottomTab = signal<BottomTab>('working');

  // ---- Chart ----
  protected readonly chartPeriods: { value: HistoryPeriod; label: string }[] = [
    { value: '1d', label: '1D' },
    { value: '7d', label: '7D' },
    { value: '1m', label: '1M' },
    { value: '1y', label: '1Y' }
  ];
  protected readonly chartPeriod = signal<HistoryPeriod>('1d');
  protected readonly chartPoints = signal<PricePoint[]>([]);
  protected readonly chartLoading = signal(true);

  /** Change from the first to the last price on the chart. */
  protected readonly periodChange = computed(() => {
    const points = this.chartPoints();
    if (points.length < 2) {
      return null;
    }
    const first = points[0].value;
    const amount = points[points.length - 1].value - first;
    return { amount, ratio: amount / first };
  });

  // ---- Working orders ----
  protected readonly workingOrders = signal<OrderHistoryResult[]>([]);
  /** Orders being moved or canceled right now. */
  protected readonly busyOrderIds = signal<number[]>([]);
  protected readonly workingNotice = signal<WorkingNotice | null>(null);
  private readonly refreshWorking$ = new Subject<void>();
  /** Ids last seen working, to notice the ones that leave the list because they filled. */
  private seenWorking: Set<number> | null = null;

  // ---- Order tracker ----
  /** A new object each time an order is (re)tracked, so tracking the same order again restarts the poll. */
  private readonly tracking = signal<{ orderId: number } | null>(null);
  protected readonly trackedOrderId = computed(() => this.tracking()?.orderId ?? null);
  /** The tracked order as last seen, before the next poll brings a fresher copy. */
  private readonly trackedSnapshot = signal<OrderHistoryResult | null>(null);
  protected readonly trackedEvents = signal<OrderEvent[]>([]);
  protected readonly todaysOrders = signal<OrderHistoryResult[]>([]);
  /** The last order sent, so a rejected or failed one can be tried again. */
  protected lastRequest: OrderRequest | null = null;

  protected readonly instrumentGroups = computed(() =>
    INSTRUMENT_GROUPS
      .map(group => ({ label: group.label, instruments: this.instruments().filter(group.matches) }))
      .filter(group => group.instruments.length > 0));

  protected readonly instrument = computed(() =>
    this.instruments().find(i => i.instrumentId === this.instrumentId()) ?? null);

  /** The instrument and side to keep when switching between the simple and advanced views. */
  protected readonly viewParams = computed(() => ({ ticker: this.instrument()?.ticker ?? null, side: this.side() }));

  protected readonly cash = computed(() => this.portfolio()?.balance ?? 0);
  /** Cash the account's working buy orders could spend, so not free for another order. */
  protected readonly heldCash = computed(() => this.portfolio()?.cashHeldForOrders ?? 0);
  protected readonly availableCash = computed(() => this.cash() - this.heldCash());

  /** How much of the selected instrument the selected account holds. */
  protected readonly owned = computed(() => {
    const ticker = this.instrument()?.ticker;
    return this.portfolio()?.holdings.find(h => h.ticker === ticker)?.quantity ?? 0;
  });

  protected readonly isMarket = computed(() => this.kind() === 'MARKET');

  protected readonly validQuantity = computed(() => {
    const q = this.quantity();
    return q != null && q > 0 ? q : null;
  });

  protected readonly validTrigger = computed(() => {
    const p = this.triggerPrice();
    return !this.isMarket() && p != null && p > 0 ? p : null;
  });

  /** The price the order is costed at: the live price for a market order, otherwise its limit or stop. */
  protected readonly orderPrice = computed(() => this.isMarket() ? this.quote()?.price ?? null : this.validTrigger());

  /** quantity × price: what a buy costs or a sell brings in. */
  protected readonly estimate = computed(() => {
    const q = this.validQuantity();
    const price = this.orderPrice();
    return q != null && price != null ? q * price : null;
  });

  /** A market buy spends its cash now; a limit or stop buy holds it until it fills or is canceled. */
  protected readonly cashAfter = computed(() => {
    const estimate = this.estimate() ?? 0;
    return this.side() === 'BUY' ? this.availableCash() - estimate : this.availableCash() + (this.isMarket() ? estimate : 0);
  });

  protected readonly ownedAfter = computed(() => {
    const q = this.validQuantity() ?? 0;
    return this.side() === 'BUY' ? this.owned() + q : this.owned() - q;
  });

  /** The most this account can trade: its available cash for a buy, all its holding for a sell. */
  protected readonly maxQuantity = computed(() => {
    if (this.side() === 'SELL') {
      return this.owned();
    }
    const price = this.orderPrice();
    return price ? Math.max(roundDown(this.availableCash() / price), 0) : 0;
  });

  /** The limit or stop price is on the side of the market where the order would fill at once. */
  protected readonly triggerError = computed(() => {
    const p = this.triggerPrice();
    const market = this.quote()?.price;
    if (this.isMarket() || p == null) {
      return '';
    }
    if (p <= 0) {
      return 'Enter a price above zero.';
    }
    if (market == null) {
      return '';
    }
    const buy = this.side() === 'BUY';
    const waitsForFall = (this.kind() === 'LIMIT') === buy;
    if (waitsForFall ? p < market : p > market) {
      return '';
    }
    const name = orderName(this.side(), this.kind() as ChartOrderKind).toLowerCase();
    return `A ${name} waits for the price to ${waitsForFall ? 'fall' : 'rise'} to it, so set it `
      + `${waitsForFall ? 'below' : 'above'} the current ${formatMoney(market)}.`;
  });

  /** The other kind, which waits on the side of the market the price is on now. */
  protected readonly otherKind = computed<OrderKind>(() => this.kind() === 'LIMIT' ? 'STOP' : 'LIMIT');

  /** What the limit or stop order will do, in words. */
  protected readonly triggerHelp = computed(() => {
    const p = this.validTrigger();
    if (p == null) {
      return '';
    }
    const at = formatMoney(p);
    const buy = this.side() === 'BUY';
    if (this.kind() === 'LIMIT') {
      return buy
        ? `Buys at ${at} or lower, once the price falls there. Your cash for it is held until then.`
        : `Sells at ${at} or higher, once the price rises there.`;
    }
    return buy
      ? `Once the price rises to ${at}, buys at the market price, which can be above your stop.`
      : `Once the price falls to ${at}, sells at the market price, which in a fast market can be below your stop.`;
  });

  /** Spotted before sending; the order can still be sent, and the backend will reject it with its reason. */
  protected readonly warning = computed(() => {
    const q = this.validQuantity();
    if (q == null || !this.instrument()) {
      return '';
    }
    if (this.side() === 'BUY' && (this.estimate() ?? 0) > this.availableCash()) {
      return this.heldCash() > 0
        ? `This costs more than the ${formatMoney(Math.max(this.availableCash(), 0))} available (some cash is held for working orders), so it will be rejected.`
        : 'This costs more than the cash in this account, so it will be rejected.';
    }
    if (this.side() === 'SELL' && q > this.owned()) {
      return this.owned() > 0
        ? `You only hold ${formatQuantity(this.owned())} ${this.instrument()!.ticker}, so this will be rejected.`
        : `You don't hold any ${this.instrument()!.ticker}, so this will be rejected.`;
    }
    return '';
  });

  protected readonly quantityError = computed(() => {
    const q = this.quantity();
    if (q == null) {
      return '';
    }
    if (q <= 0) {
      return 'Enter a quantity above zero.';
    }
    const scaled = q * 10 ** QUANTITY_DECIMALS;
    if (Math.abs(scaled - Math.round(scaled)) > 1e-6) {
      return `Use at most ${QUANTITY_DECIMALS} decimal places.`;
    }
    return '';
  });

  /** A market order needs the market open; a limit or stop order can wait for it, unless trading is suspended. */
  protected readonly canSubmit = computed(() =>
    !!this.accountId() && !!this.instrument() && this.validQuantity() != null && !this.quantityError()
    && !!this.quote() && !this.submitting()
    && (this.isMarket() ? !this.notTradable() : this.validTrigger() != null && !this.triggerError() && !this.suspended()));

  /** Why the chart's Place button is disabled, if it is. */
  protected readonly placeHint = computed(() =>
    this.validQuantity() == null ? 'Enter a quantity in the ticket first' : this.triggerError() || this.quantityError());

  // ---- Chart lines ----

  /** The selected account's working orders for the charted instrument. */
  protected readonly chartOrders = computed<ChartOrder[]>(() => {
    const ticker = this.instrument()?.ticker;
    return this.workingOrders()
      .filter(o => o.ticker === ticker && o.accountId === this.accountId() && o.triggerPrice != null)
      .map(o => ({
        orderId: o.orderId,
        side: o.orderType as OrderSide,
        kind: o.orderKind as ChartOrderKind,
        quantity: o.quantity,
        price: o.triggerPrice!
      }));
  });

  /** The limit or stop order in the ticket, drawn as a dashed line until it is placed. */
  protected readonly draft = computed<ChartDraft | null>(() => {
    const price = this.validTrigger();
    return price == null ? null : {
      side: this.side(), kind: this.kind() as ChartOrderKind, quantity: this.validQuantity(), price
    };
  });

  protected readonly trackedOrder = computed(() => {
    const id = this.trackedOrderId();
    if (id == null) {
      return null;
    }
    return this.todaysOrders().find(o => o.orderId === id) ?? this.trackedSnapshot();
  });

  protected readonly trackedIsFinal = computed(() => {
    const status = this.trackedOrder()?.orderStatus;
    return !!status && FINAL_STATUSES.includes(status);
  });

  protected readonly steps = computed<TrackerStep[]>(() => {
    const order = this.trackedOrder();
    if (!order) {
      return [];
    }
    const status = order.orderStatus;
    const eventTime = (to: OrderStatus) => this.trackedEvents().find(e => e.toStatus === to)?.createdAt;
    const value = order.quantity * (order.executionPrice ?? order.triggerPrice ?? order.price);
    const waits = order.triggerPrice != null;

    const submitted: TrackerStep = {
      title: 'Submitted',
      detail: `${this.orderLabel(order)} ${formatQuantity(order.quantity)} ${order.ticker} sent to the desk`,
      state: 'done',
      time: eventTime('SUBMITTED') ?? order.submittedAt
    };

    let checked: TrackerStep;
    if (status === 'SUBMITTED') {
      checked = { title: 'Checking', detail: 'Checking cash, holdings and price…', state: 'current' };
    } else if (status === 'REJECTED') {
      checked = { title: 'Rejected', detail: this.headline(order.rejectionReason), state: 'failed', time: eventTime('REJECTED') };
    } else if (!waits) {
      checked = { title: 'Accepted', detail: 'Passed the trading checks', state: 'done', time: eventTime('ACCEPTED') };
    } else if (status === 'WORKING') {
      checked = {
        title: 'Working', state: 'current', time: eventTime('WORKING'),
        detail: `Waiting for the price to ${this.waitsForFall(order) ? 'fall' : 'rise'} to ${this.money(order.triggerPrice!)}`
      };
    } else if (status === 'CANCELED') {
      checked = { title: 'Canceled', detail: this.headline(order.rejectionReason), state: 'skipped', time: eventTime('CANCELED') };
    } else {
      checked = {
        title: 'Triggered', detail: `The price reached your ${order.orderKind === 'STOP' ? 'stop' : 'limit'} of ${this.money(order.triggerPrice!)}`,
        state: 'done', time: order.triggeredAt ?? eventTime('ACCEPTED')
      };
    }

    let filled: TrackerStep;
    switch (status) {
      case 'FILLED':
        filled = {
          title: 'Filled',
          detail: `${formatQuantity(order.quantity)} ${order.ticker} at ${this.money(order.executionPrice ?? order.price)}, `
            + `${order.orderType === 'BUY' ? 'costing' : 'bringing in'} ${this.money(value)}`,
          state: 'done',
          time: order.executedAt ?? eventTime('FILLED')
        };
        break;
      case 'FAILED':
        filled = { title: 'Failed', detail: this.headline(order.rejectionReason), state: 'failed', time: eventTime('FAILED') };
        break;
      case 'ACCEPTED':
        filled = {
          title: 'Filling',
          detail: waits ? 'Filling at the market price…' : `Waiting to fill at ${this.money(order.price)}…`,
          state: 'current'
        };
        break;
      default:
        filled = {
          title: 'Fill',
          detail: status === 'REJECTED' || status === 'CANCELED' ? 'Not attempted'
            : status === 'WORKING' ? 'Fills once the price gets there' : 'Waiting for the checks',
          state: 'skipped'
        };
    }
    return [submitted, checked, filled];
  });

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const wantedTicker = params.get('ticker')?.toUpperCase();
    const wantedSide = params.get('side')?.toUpperCase();
    if (wantedSide === 'BUY' || wantedSide === 'SELL') {
      this.side.set(wantedSide);
    }

    forkJoin({
      accounts: this.accountService.getAccounts(),
      instruments: this.instrumentService.getInstruments()
    }).subscribe({
      next: ({ accounts, instruments }) => {
        this.accounts.set(accounts);
        this.accountId.set(accounts[0]?.accountId ?? null);
        this.instruments.set(instruments);
        const wanted = instruments.find(i => i.ticker.toUpperCase() === wantedTicker);
        this.instrumentId.set((wanted ?? this.instrumentGroups()[0]?.instruments[0])?.instrumentId ?? null);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Could not load your accounts and instruments. Is the backend running?');
        this.loading.set(false);
      }
    });

    // Cash and holdings for the chosen account
    toObservable(this.accountId).pipe(
      filter(isPresent),
      switchMap(id => this.accountService.getPortfolio(id).pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(portfolio => this.portfolio.set(portfolio));

    // Live price for the chosen instrument, every 3 seconds; switchMap stops the old poll on change
    toObservable(this.instrumentId).pipe(
      switchMap(id => {
        this.quote.set(null);
        this.quoteError.set('');
        this.priceMove.set(null);
        return id == null ? of() : interval(PRICE_REFRESH_MS).pipe(
          startWith(0),
          switchMap(() => this.instrumentService.getQuote(id).pipe(
            catchError((error: HttpErrorResponse) => {
              this.quoteError.set(error.status === 404 ? 'No price yet for this instrument.' : 'Could not load the price.');
              return of(null);
            })
          ))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(quote => {
      if (!quote) {
        return;
      }
      const previous = this.quote()?.price;
      if (previous != null && quote.price !== previous) {
        this.priceMove.set(quote.price > previous ? 'up' : 'down');
      }
      this.quoteError.set('');
      this.quote.set(quote);
      const delayMs = (quote.delayMinutes ?? 0) * 60_000;
      this.quoteIsLive.set(Date.now() - new Date(quote.timestamp).getTime() < LIVE_QUOTE_MS + delayMs);
      this.appendToChart(quote);
    });

    // Price history for the chart. An instrument with no trades today (a closed stock market) shows the week.
    let fellBackFor: string | null = null;
    combineLatest([toObservable(this.instrument), toObservable(this.chartPeriod)]).pipe(
      filter(([instrument]) => this.advanced && instrument != null),
      switchMap(([instrument, period]) => {
        this.chartLoading.set(true);
        return this.historyService.getPriceHistory(instrument!.ticker, period).pipe(
          catchError(() => of([])),
          map(prices => ({ ticker: instrument!.ticker, period, prices }))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ ticker, period, prices }) => {
      if (period === '1d' && prices.length < 2 && fellBackFor !== ticker) {
        fellBackFor = ticker;
        this.chartPeriod.set('7d');
        return;
      }
      // The API returns newest first; the chart reads left to right
      this.chartPoints.set(prices.map(p => ({ time: p.quoteTimestamp, value: p.price })).reverse());
      this.chartLoading.set(false);
      const quote = this.quote();
      if (quote) {
        this.appendToChart(quote);
      }
    });

    // Working limit and stop orders, as often as prices change, so a triggered one disappears promptly
    merge(interval(PRICE_REFRESH_MS).pipe(startWith(0)), this.refreshWorking$).pipe(
      switchMap(() => this.orderService.getWorkingOrders().pipe(catchError(() => of(null)))),
      filter(isPresent),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(orders => this.updateWorking(orders));

    // Today's orders, so new ones show up beside the ticket
    interval(15_000).pipe(
      startWith(0),
      switchMap(() => this.orderService.getOrders('1d').pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(orders => orders && this.todaysOrders.set(orders));

    // Follow the tracked order until it is final or WORKING (the working orders poll watches it from there),
    // giving up after a minute. Orders fill about half a second after they are accepted, so check often enough
    // to show each step.
    toObservable(this.tracking).pipe(
      switchMap(tracking => tracking == null ? of() : timer(0, TRACK_POLL_MS).pipe(
        switchMap(() => forkJoin({
          orders: this.orderService.getOrders('1d'),
          events: this.orderService.getOrderEvents(tracking.orderId)
        }).pipe(catchError(() => of(null)))),
        filter(isPresent),
        map(result => ({ ...result, order: result.orders.find(o => o.orderId === tracking.orderId) })),
        // An order from an earlier day isn't in today's list; its snapshot is the latest copy then
        takeWhile(result => !this.settled((result.order ?? this.trackedSnapshot())?.orderStatus), true),
        take(60_000 / TRACK_POLL_MS)
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ orders, events, order }) => {
      if (order) {
        this.trackedSnapshot.set(order);
      }
      this.todaysOrders.set(orders);
      this.trackedEvents.set(events);
      if (order && FINAL_STATUSES.includes(order.orderStatus)) {
        this.refreshBalances();
      }
    });
  }

  protected setSide(side: OrderSide): void {
    this.side.set(side);
  }

  /** Switching to a limit or stop order starts its price a little way from the market, on the side it waits. */
  protected setKind(kind: OrderKind): void {
    this.kind.set(kind);
    if (kind === 'MARKET') {
      return;
    }
    const market = this.quote()?.price;
    if (market != null && (this.triggerPrice() == null || this.triggerError())) {
      const waitsForFall = (kind === 'LIMIT') === (this.side() === 'BUY');
      const distance = STARTING_DISTANCE[kind];
      this.triggerPrice.set(roundPrice(market * (waitsForFall ? 1 - distance : 1 + distance)));
    }
  }

  protected setTriggerPrice(value: number | null): void {
    this.triggerPrice.set(value);
  }

  protected setAccount(value: string): void {
    this.accountId.set(Number(value));
  }

  protected setChartPeriod(period: HistoryPeriod): void {
    this.chartPeriod.set(period);
  }

  /** What the instrument picker shows for an instrument, and what its search matches against. */
  protected readonly instrumentLabel = (i: Instrument) => `${i.ticker} · ${i.instrumentName}`;

  protected readonly sameInstrument = (a: Instrument | null | undefined, b: Instrument | null | undefined) =>
    a?.instrumentId === b?.instrumentId;

  /** Clearing the picker's text doesn't clear the order's instrument; only picking another one changes it. */
  protected selectInstrument(instrument: Instrument | null | undefined): void {
    if (instrument && instrument.instrumentId !== this.instrumentId()) {
      this.instrumentId.set(instrument.instrumentId);
      // A limit or stop price belongs to the instrument it was set for
      this.triggerPrice.set(null);
      this.kind.set('MARKET');
    }
  }

  /** Charts a working order's instrument, e.g. from the working orders list. */
  protected showOnChart(order: OrderHistoryResult): void {
    const instrument = this.instruments().find(i => i.ticker === order.ticker);
    if (instrument) {
      this.selectInstrument(instrument);
    }
    if (order.accountId != null) {
      this.accountId.set(order.accountId);
    }
  }

  protected setQuantity(value: number | null): void {
    this.quantity.set(value);
  }

  protected useMax(): void {
    this.quantity.set(this.maxQuantity());
  }

  protected submit(): void {
    const instrument = this.instrument();
    const accountId = this.accountId();
    const quantity = this.validQuantity();
    if (!this.canSubmit() || !instrument || accountId == null || quantity == null) {
      return;
    }
    const request: OrderRequest = { accountId, ticker: instrument.ticker, orderType: this.side(), quantity };
    if (!this.isMarket()) {
      request.orderKind = this.kind();
      request.triggerPrice = this.validTrigger()!;
    }
    this.send(request);
  }

  /** Sends the last order again, e.g. after it failed because the price moved. */
  protected retry(): void {
    if (this.lastRequest && !this.submitting()) {
      this.send(this.lastRequest);
    }
  }

  /** Opens an earlier order in the tracker. */
  protected track(order: OrderHistoryResult): void {
    this.startTracking(order);
    this.showTab('status');
  }

  protected showTab(tab: BottomTab): void {
    this.bottomTab.set(tab);
    this.bottomOpen.set(true);
  }

  protected tabClass(tab: BottomTab): string {
    const base = 'flex items-center border-b-2 px-3 py-2.5 text-sm font-medium whitespace-nowrap transition-colors';
    return this.bottomTab() === tab && this.bottomOpen()
      ? `${base} border-primary text-foreground`
      : `${base} border-transparent text-muted-foreground hover:text-foreground`;
  }

  protected togglePanel(): void {
    this.panelOpen.update(open => !open);
  }

  // ---- From the chart ----

  /** An order started from the chart's + menu: set up in the ticket, ready for a quantity and Place. */
  protected stageFromChart(staged: { side: OrderSide; kind: ChartOrderKind; price: number }): void {
    this.side.set(staged.side);
    this.kind.set(staged.kind);
    this.triggerPrice.set(staged.price);
    this.submitError.set(null);
    this.panelOpen.set(true);
    if (this.validQuantity() == null) {
      setTimeout(() => this.quantityInput()?.nativeElement.focus());
    }
  }

  protected draftFromChart(change: { price: number; kind: ChartOrderKind }): void {
    this.kind.set(change.kind);
    this.triggerPrice.set(change.price);
  }

  protected discardDraft(): void {
    this.kind.set('MARKET');
    this.triggerPrice.set(null);
  }

  /** Moves a working order to the price its line was dropped at. */
  protected moveWorking(move: { order: ChartOrder; price: number }): void {
    const { order, price } = move;
    this.setBusy(order.orderId, true);
    this.orderService.moveOrder(order.orderId, price).subscribe({
      next: moved => {
        this.setBusy(order.orderId, false);
        this.workingNotice.set({
          tone: 'ok', text: `Moved your ${orderName(order.side, order.kind).toLowerCase()} to ${formatMoney(price)} (now order #${moved.orderId}).`
        });
        this.workingOrders.update(orders => [...orders.filter(o => o.orderId !== order.orderId), moved]);
        this.seenWorking?.add(moved.orderId);
        this.seenWorking?.delete(order.orderId);
        if (this.trackedOrderId() === order.orderId) {
          this.startTracking(moved);
        }
        this.refreshAfterWorkingChange();
      },
      error: (error: HttpErrorResponse) => {
        this.setBusy(order.orderId, false);
        this.workingNotice.set({ tone: 'error', text: `Couldn't move order #${order.orderId}: ${this.errorText(error)}` });
        this.refreshWorking$.next();
      }
    });
  }

  protected cancelWorking(order: { orderId: number }): void {
    this.setBusy(order.orderId, true);
    this.orderService.cancelOrder(order.orderId).subscribe({
      next: canceled => {
        this.setBusy(order.orderId, false);
        this.workingNotice.set({ tone: 'ok', text: `Canceled order #${order.orderId}, ${this.describeOrder(canceled)}.` });
        this.workingOrders.update(orders => orders.filter(o => o.orderId !== order.orderId));
        this.seenWorking?.delete(order.orderId);
        if (this.trackedOrderId() === order.orderId) {
          this.startTracking(canceled);
        }
        this.refreshAfterWorkingChange();
      },
      error: (error: HttpErrorResponse) => {
        this.setBusy(order.orderId, false);
        this.workingNotice.set({ tone: 'error', text: `Couldn't cancel order #${order.orderId}: ${this.errorText(error)}` });
        this.refreshWorking$.next();
      }
    });
  }

  protected headline(reason?: string): string {
    if (!reason) {
      return 'The order could not be completed';
    }
    return REASON_HEADLINES.find(r => r.pattern.test(reason))?.headline ?? reason;
  }

  protected statusVariant(status: OrderStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
    return status === 'FILLED' ? 'default' : status === 'REJECTED' ? 'destructive'
      : status === 'FAILED' || status === 'CANCELED' ? 'outline' : 'secondary';
  }

  /** "Buy", or "Buy limit", "Stop-loss" and so on for limit and stop orders. */
  protected orderLabel(o: { orderType: string; orderKind?: OrderKind }): string {
    return o.orderKind === 'LIMIT' || o.orderKind === 'STOP'
      ? orderName(o.orderType as OrderSide, o.orderKind)
      : o.orderType === 'BUY' ? 'Buy' : 'Sell';
  }

  /** How far the market is from a working order's price, e.g. "2.4% away". */
  protected distance(order: OrderHistoryResult): string {
    const market = order.ticker === this.instrument()?.ticker ? this.quote()?.price : null;
    if (market == null || order.triggerPrice == null) {
      return '';
    }
    return `${(Math.abs(market - order.triggerPrice) / market * 100).toFixed(1)}% away`;
  }

  protected isBusy(orderId: number): boolean {
    return this.busyOrderIds().includes(orderId);
  }

  private send(request: OrderRequest): void {
    this.lastRequest = request;
    this.submitting.set(true);
    this.submitError.set(null);

    this.orderService.placeOrder(request).subscribe({
      next: order => {
        this.submitting.set(false);
        this.startTracking(order);
        this.afterSending(order.orderStatus === 'WORKING' ? 'working' : 'status');
        if (order.orderStatus === 'WORKING') {
          // Now a solid line on the chart instead of the dashed draft
          this.workingOrders.update(orders => [...orders, order]);
          this.seenWorking?.add(order.orderId);
          this.triggerPrice.set(null);
          this.kind.set('MARKET');
          this.refreshAfterWorkingChange();
        }
      },
      error: (error: HttpErrorResponse) => {
        this.submitting.set(false);
        const rejection = error.status === 422 ? error.error as OrderRejection : null;
        if (rejection?.orderId != null) {
          // Recorded as REJECTED: show it in the tracker straight away, like any other order
          this.startTracking({
            orderId: rejection.orderId,
            accountId: request.accountId,
            ticker: request.ticker,
            orderType: request.orderType,
            quantity: request.quantity,
            price: this.quote()?.price ?? 0,
            orderStatus: 'REJECTED',
            orderDate: new Date().toISOString(),
            submittedAt: new Date().toISOString(),
            rejectionReason: rejection.message,
            orderKind: request.orderKind,
            triggerPrice: request.triggerPrice
          });
          this.afterSending('status');
        } else if (rejection) {
          this.submitError.set({ headline: this.headline(rejection.message), detail: rejection.message });
        } else {
          this.submitError.set({ headline: 'The order was not sent', detail: this.errorText(error) });
        }
      }
    });
  }

  /** Shows how the order went; on a small screen the ticket closes so the chart and that tab can be seen. */
  private afterSending(tab: BottomTab): void {
    this.showTab(tab);
    if (!isWide()) {
      this.panelOpen.set(false);
    }
  }

  private startTracking(order: OrderHistoryResult): void {
    this.trackedEvents.set([]);
    this.trackedSnapshot.set(order);
    // Put it at the top of today's list now rather than at the next poll
    this.todaysOrders.update(orders => [order, ...orders.filter(o => o.orderId !== order.orderId)]);
    this.tracking.set({ orderId: order.orderId });
  }

  /** Final, or WORKING: nothing more happens to it until its price is reached. */
  private settled(status?: OrderStatus): boolean {
    return !!status && (FINAL_STATUSES.includes(status) || status === 'WORKING');
  }

  /**
   * Takes in the latest working orders. Ones that left the list since the last look, without this page moving or
   * canceling them, were triggered: say how they ended, and refresh cash, holdings and the tracker.
   */
  private updateWorking(orders: OrderHistoryResult[]): void {
    const busy = new Set(this.busyOrderIds());
    this.workingOrders.set(orders.map(o => busy.has(o.orderId) ? this.workingOrders().find(w => w.orderId === o.orderId) ?? o : o));
    const ids = new Set(orders.map(o => o.orderId));
    const gone = this.seenWorking ? [...this.seenWorking].filter(id => !ids.has(id) && !busy.has(id)) : [];
    this.seenWorking = ids;
    if (gone.length === 0) {
      return;
    }
    this.orderService.getOrders().pipe(catchError(() => of([] as OrderHistoryResult[]))).subscribe(all => {
      const ended = all.filter(o => gone.includes(o.orderId));
      const filled = ended.filter(o => o.orderStatus === 'FILLED' || o.orderStatus === 'ACCEPTED');
      const other = ended.find(o => o.orderStatus === 'FAILED' || o.orderStatus === 'CANCELED');
      if (filled.length > 0) {
        this.showTab('working');
        this.workingNotice.set({
          tone: 'ok',
          text: filled.map(o => `Your ${this.orderLabel(o).toLowerCase()} for ${formatQuantity(o.quantity)} ${o.ticker} `
            + (o.executionPrice != null ? `filled at ${formatMoney(o.executionPrice)}.` : 'was triggered and is filling.')).join(' ')
        });
      } else if (other) {
        this.workingNotice.set({
          tone: 'error', text: `Order #${other.orderId} ${other.orderStatus === 'FAILED' ? 'could not fill' : 'was canceled'}: ${other.rejectionReason}`
        });
      }
      const tracked = this.trackedOrderId();
      const trackedEnded = ended.find(o => o.orderId === tracked);
      if (trackedEnded) {
        this.startTracking(trackedEnded);
      }
      this.refreshBalances();
    });
  }

  private refreshAfterWorkingChange(): void {
    this.refreshBalances();
    this.refreshWorking$.next();
  }

  private setBusy(orderId: number, busy: boolean): void {
    this.busyOrderIds.update(ids => busy ? [...ids, orderId] : ids.filter(id => id !== orderId));
  }

  /** The live price, added to the end of the chart as it arrives. */
  private appendToChart(quote: Quote): void {
    const points = this.chartPoints();
    const last = points[points.length - 1];
    if (!last || Date.parse(quote.timestamp) <= Date.parse(last.time)) {
      return;
    }
    this.chartPoints.set([...points, { time: quote.timestamp, value: quote.price }]);
  }

  private waitsForFall(order: OrderHistoryResult): boolean {
    return (order.orderKind === 'LIMIT') === (order.orderType === 'BUY');
  }

  private describeOrder(order: OrderHistoryResult): string {
    return `${this.orderLabel(order).toLowerCase()} ${formatQuantity(order.quantity)} ${order.ticker}`
      + (order.triggerPrice != null ? ` at ${formatMoney(order.triggerPrice)}` : '');
  }

  private errorText(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Could not reach the server. Is the backend running?';
    }
    const body = error.error as { message?: string } | string | null;
    return typeof body === 'string' ? body : body?.message ?? `The server answered ${error.status}.`;
  }

  private refreshBalances(): void {
    const id = this.accountId();
    if (id == null) {
      return;
    }
    this.accountService.getPortfolio(id).subscribe({ next: p => this.portfolio.set(p), error: () => {} });
  }

  private money(value: number): string {
    return formatMoney(value);
  }
}
