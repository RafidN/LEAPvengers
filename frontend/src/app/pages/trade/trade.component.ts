import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  catchError, filter, forkJoin, interval, map, of, startWith, switchMap, take, takeWhile, timer
} from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowDown, lucideArrowUp, lucideBan, lucideBookOpen, lucideCircleCheck, lucideCircleX, lucideClock, lucideFlaskConical,
  lucideHistory, lucideLoaderCircle, lucideRotateCcw, lucideTriangleAlert
} from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmComboboxImports } from '@spartan-ng/helm/combobox';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { AccountPortfolio, AccountResponse, AccountService } from '../../services/account.service';
import { OrderHistoryResult, OrderStatus } from '../../services/history.service';
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
  { pattern: /price moved/i, headline: 'The price changed before your order could fill' },
  { pattern: /older than/i, headline: 'There is no current price to trade on' },
  { pattern: /is closed/i, headline: 'This market is closed right now' },
  { pattern: /suspended/i, headline: 'Trading in this instrument is paused' },
  { pattern: /restarted|stopped before/i, headline: 'The order was interrupted by a platform restart' },
  { pattern: /no price/i, headline: 'This instrument has no price yet' },
  { pattern: /greater than 0/i, headline: 'Quantity must be more than zero' }
];

type StepState = 'done' | 'current' | 'failed' | 'skipped';

/** One row of the order tracker: Submitted, Checked, Filled. */
interface TrackerStep {
  title: string;
  detail: string;
  state: StepState;
  time?: string;
}

interface SimulationResult {
  side: OrderSide;
  ticker: string;
  quantity: number;
  price: number;
  total: number;
  cashAfter: number;
  holdingsAfter: number;
}

interface QuizQuestion {
  question: string;
  options: string[];
  answer: number;
  explanation: string;
}

const QUIZ_QUESTIONS: QuizQuestion[] = [
  {
    question: 'What is a quote?',
    options: ['The latest available price', 'The amount of cash in an account', 'A completed order'],
    answer: 0,
    explanation: 'A quote is the latest available price for one unit of an instrument.'
  },
  {
    question: 'What does quantity mean on an order ticket?',
    options: ['The exchange opening time', 'How many units you want to trade', 'The account number'],
    answer: 1,
    explanation: 'Quantity is the number of units you want to buy or sell.'
  },
  {
    question: 'What happens in simulation mode?',
    options: ['A real order is sent', 'Your account balance changes', 'A hypothetical outcome is calculated'],
    answer: 2,
    explanation: 'Simulation mode calculates an outcome without sending an order or changing account data.'
  },
  {
    question: 'What are holdings?',
    options: ['Assets and quantities currently owned', 'Orders that were rejected', 'A market news feed'],
    answer: 0,
    explanation: 'Holdings are the assets and quantities currently owned in an account.'
  }
];

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

/**
 * Order ticket: pick an account, instrument, side and quantity against a live price, then watch the
 * order move through Submitted, Accepted and Filled (or Rejected / Failed) in the tracker beside it.
 */
@Component({
  selector: 'app-trade',
  templateUrl: './trade.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, TitleCasePipe, FormsModule, RouterLink, NgIcon,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmComboboxImports, HlmInputImports,
    HlmLabelImports
  ],
  providers: [provideIcons({
    lucideArrowDown, lucideArrowUp, lucideBan, lucideBookOpen, lucideCircleCheck, lucideCircleX, lucideClock, lucideFlaskConical,
    lucideHistory, lucideLoaderCircle, lucideRotateCcw, lucideTriangleAlert
  })]
})
export class TradeComponent {
  private readonly accountService = inject(AccountService);
  private readonly instrumentService = inject(InstrumentService);
  private readonly orderService = inject(OrderService);
  private readonly destroyRef = inject(DestroyRef);

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
  /** Which way the price moved at the last refresh, for the up/down arrow. */
  protected readonly priceMove = signal<'up' | 'down' | null>(null);

  protected readonly side = signal<OrderSide>('BUY');
  protected readonly quantity = signal<number | null>(null);
  protected readonly learnMode = signal(false);
  protected readonly simulationMode = signal(false);
  protected readonly simulationBalance = signal<number | null>(null);
  protected readonly simulationResult = signal<SimulationResult | null>(null);
  protected readonly quizQuestions = QUIZ_QUESTIONS;
  protected readonly quizIndex = signal(0);
  protected readonly quizAnswer = signal<number | null>(null);
  protected readonly quizScore = signal(0);
  protected readonly quizComplete = signal(false);

  protected readonly quizQuestion = computed(() => this.quizQuestions[this.quizIndex()]);

  protected readonly loading = signal(true);
  protected readonly loadError = signal('');
  protected readonly submitting = signal(false);
  /** A problem with the request itself, when no order was recorded. */
  protected readonly submitError = signal<{ headline: string; detail: string } | null>(null);

  // ---- Order tracker ----
  protected readonly trackedOrderId = signal<number | null>(null);
  /** The tracked order as last seen, before the next poll brings a fresher copy. */
  private readonly trackedSnapshot = signal<OrderHistoryResult | null>(null);
  protected readonly trackedEvents = signal<OrderEvent[]>([]);
  protected readonly todaysOrders = signal<OrderHistoryResult[]>([]);
  /** The last order sent, so a rejected or failed one can be tried again. */
  private lastRequest: OrderRequest | null = null;

  protected readonly instrumentGroups = computed(() =>
    INSTRUMENT_GROUPS
      .map(group => ({ label: group.label, instruments: this.instruments().filter(group.matches) }))
      .filter(group => group.instruments.length > 0));

  protected readonly instrument = computed(() =>
    this.instruments().find(i => i.instrumentId === this.instrumentId()) ?? null);

  protected readonly cash = computed(() => this.portfolio()?.balance ?? 0);

  /** How much of the selected instrument the selected account holds. */
  protected readonly owned = computed(() => {
    const ticker = this.instrument()?.ticker;
    return this.portfolio()?.holdings.find(h => h.ticker === ticker)?.quantity ?? 0;
  });

  protected readonly validQuantity = computed(() => {
    const q = this.quantity();
    return q != null && q > 0 ? q : null;
  });

  /** quantity × live price: what a buy costs or a sell brings in. */
  protected readonly estimate = computed(() => {
    const q = this.validQuantity();
    const price = this.quote()?.price;
    return q != null && price != null ? q * price : null;
  });

  protected readonly cashAfter = computed(() => {
    const estimate = this.estimate() ?? 0;
    return this.side() === 'BUY' ? this.cash() - estimate : this.cash() + estimate;
  });

  protected readonly ownedAfter = computed(() => {
    const q = this.validQuantity() ?? 0;
    return this.side() === 'BUY' ? this.owned() + q : this.owned() - q;
  });

  /** The most this account can trade right now: all its cash for a buy, all its holding for a sell. */
  protected readonly maxQuantity = computed(() => {
    if (this.side() === 'SELL') {
      return this.owned();
    }
    const price = this.quote()?.price;
    return price ? roundDown(this.cash() / price) : 0;
  });

  /** Spotted before sending; the order can still be sent, and the backend will reject it with its reason. */
  protected readonly warning = computed(() => {
    const q = this.validQuantity();
    if (q == null || !this.instrument()) {
      return '';
    }
    if (this.side() === 'BUY' && (this.estimate() ?? 0) > this.cash()) {
      return 'This costs more than the cash in this account, so it will be rejected.';
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

  protected readonly canSubmit = computed(() =>
    !!this.accountId() && !!this.instrument() && this.validQuantity() != null && !this.quantityError()
    && !!this.quote() && !this.notTradable() && !this.submitting());

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
    const value = order.quantity * (order.executionPrice ?? order.price);

    const submitted: TrackerStep = {
      title: 'Submitted',
      detail: `${order.orderType === 'BUY' ? 'Buy' : 'Sell'} ${formatQuantity(order.quantity)} ${order.ticker} sent to the desk`,
      state: 'done',
      time: eventTime('SUBMITTED') ?? order.submittedAt
    };

    const checked: TrackerStep = status === 'SUBMITTED'
      ? { title: 'Checking', detail: 'Checking cash, holdings and price…', state: 'current' }
      : status === 'REJECTED'
        ? { title: 'Rejected', detail: this.headline(order.rejectionReason), state: 'failed', time: eventTime('REJECTED') }
        : { title: 'Accepted', detail: 'Passed the trading checks', state: 'done', time: eventTime('ACCEPTED') };

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
        filled = { title: 'Filling', detail: `Waiting to fill at ${this.money(order.price)}…`, state: 'current' };
        break;
      default:
        filled = { title: 'Fill', detail: status === 'REJECTED' ? 'Not attempted' : 'Waiting for the checks', state: 'skipped' };
    }
    return [submitted, checked, filled];
  });

  constructor() {
    const params = inject(ActivatedRoute).snapshot.queryParamMap;
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
    });

    // Today's orders, so new ones show up beside the ticket
    interval(15_000).pipe(
      startWith(0),
      switchMap(() => this.orderService.getOrders('1d').pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(orders => orders && this.todaysOrders.set(orders));

    // Follow the tracked order until it is FILLED, REJECTED or FAILED (giving up after a minute).
    // Orders fill about half a second after they are accepted, so check often enough to show each step.
    toObservable(this.trackedOrderId).pipe(
      switchMap(id => id == null ? of() : timer(0, TRACK_POLL_MS).pipe(
        switchMap(() => forkJoin({
          orders: this.orderService.getOrders('1d'),
          events: this.orderService.getOrderEvents(id)
        }).pipe(catchError(() => of(null)))),
        filter(isPresent),
        map(result => ({ ...result, order: result.orders.find(o => o.orderId === id) })),
        takeWhile(result => !FINAL_STATUSES.includes(result.order?.orderStatus as OrderStatus), true),
        take(60_000 / TRACK_POLL_MS)
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ orders, events, order }) => {
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

  protected setAccount(value: string): void {
    this.accountId.set(Number(value));
  }

  /** What the instrument picker shows for an instrument, and what its search matches against. */
  protected readonly instrumentLabel = (i: Instrument) => `${i.ticker} · ${i.instrumentName}`;

  protected readonly sameInstrument = (a: Instrument | null | undefined, b: Instrument | null | undefined) =>
    a?.instrumentId === b?.instrumentId;

  /** Clearing the picker's text doesn't clear the order's instrument; only picking another one changes it. */
  protected selectInstrument(instrument: Instrument | null | undefined): void {
    if (instrument) {
      this.instrumentId.set(instrument.instrumentId);
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
    if (this.simulationMode()) {
      this.simulate(instrument.ticker, quantity);
      return;
    }
    this.send({ accountId, ticker: instrument.ticker, orderType: this.side(), quantity });
  }

  protected simulate(ticker: string, quantity: number): void {
    const price = this.quote()?.price;
    if (price == null) {
      return;
    }
    const startingCash = this.simulationBalance() ?? this.cash();
    const simulatedCashAfter = this.side() === 'BUY'
      ? startingCash - quantity * price
      : startingCash + quantity * price;
    this.simulationResult.set({
      side: this.side(),
      ticker,
      quantity,
      price,
      total: quantity * price,
      cashAfter: simulatedCashAfter,
      holdingsAfter: this.ownedAfter()
    });
  }

  protected setSimulationBalance(value: number | null): void {
    this.simulationBalance.set(value);
    this.clearSimulation();
  }

  protected clearSimulation(): void {
    this.simulationResult.set(null);
  }

  protected answerQuiz(option: number): void {
    if (this.quizAnswer() !== null || this.quizComplete()) {
      return;
    }
    this.quizAnswer.set(option);
    if (option === this.quizQuestion().answer) {
      this.quizScore.update(score => score + 1);
    }
  }

  protected nextQuizQuestion(): void {
    if (this.quizIndex() >= this.quizQuestions.length - 1) {
      this.quizComplete.set(true);
      return;
    }
    this.quizIndex.update(index => index + 1);
    this.quizAnswer.set(null);
  }

  protected resetQuiz(): void {
    this.quizIndex.set(0);
    this.quizAnswer.set(null);
    this.quizScore.set(0);
    this.quizComplete.set(false);
  }

  /** Sends the last order again, e.g. after it failed because the price moved. */
  protected retry(): void {
    if (this.lastRequest && !this.submitting()) {
      this.send(this.lastRequest);
    }
  }

  /** Opens an earlier order of today's in the tracker. */
  protected track(order: OrderHistoryResult): void {
    this.trackedEvents.set([]);
    this.trackedSnapshot.set(order);
    this.trackedOrderId.set(order.orderId);
  }

  protected headline(reason?: string): string {
    if (!reason) {
      return 'The order could not be completed';
    }
    return REASON_HEADLINES.find(r => r.pattern.test(reason))?.headline ?? reason;
  }

  protected statusVariant(status: OrderStatus): 'default' | 'secondary' | 'destructive' | 'outline' {
    return status === 'FILLED' ? 'default' : status === 'REJECTED' ? 'destructive' : status === 'FAILED' ? 'outline' : 'secondary';
  }

  private send(request: OrderRequest): void {
    this.lastRequest = request;
    this.submitting.set(true);
    this.submitError.set(null);

    this.orderService.placeOrder(request).subscribe({
      next: order => {
        this.submitting.set(false);
        this.startTracking(order);
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
            rejectionReason: rejection.message
          });
        } else if (rejection) {
          this.submitError.set({ headline: this.headline(rejection.message), detail: rejection.message });
        } else {
          this.submitError.set({
            headline: 'The order was not sent',
            detail: error.status === 0 ? 'Could not reach the server. Is the backend running?'
              : typeof error.error === 'string' ? error.error : `The server answered ${error.status}.`
          });
        }
      }
    });
  }

  private startTracking(order: OrderHistoryResult): void {
    this.trackedEvents.set([]);
    this.trackedSnapshot.set(order);
    // Put it at the top of today's list now rather than at the next poll
    this.todaysOrders.update(orders => [order, ...orders.filter(o => o.orderId !== order.orderId)]);
    this.trackedOrderId.set(order.orderId);
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
