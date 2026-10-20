import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { PercentPipe } from '@angular/common';
import { DisplayCurrencyPipe } from '../../shared/display-currency.pipe';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, interval, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowLeftRight, lucideSearch, lucideShieldAlert } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { PriceChartComponent, PricePoint } from '../../components/price-chart/price-chart.component';
import { MarketMoversComponent } from '../../components/market-movers/market-movers.component';
import { MarketNewsComponent } from '../../components/market-news/market-news.component';
import { HistoryPeriod, HistoryService } from '../../services/history.service';
import { InstrumentSearchService, PriceQuoteResult, WATCHLIST } from '../../services/instrument-search.service';
import { PRICE_REFRESH_MS } from '../../services/instrument.service';
import { AuthService } from '../../services/auth.service';
import { PreferencesService } from '../../services/preferences.service';
import { DecimalPipe } from '@angular/common';

/** Search instruments and chart their price history; below, today's top movers and market news. */
@Component({
  selector: 'app-markets',
  templateUrl: './markets.component.html',
  imports: [
    DisplayCurrencyPipe, PercentPipe, FormsModule, NgIcon, PriceChartComponent, RouterLink, MarketMoversComponent, MarketNewsComponent,
    HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmTableImports, DecimalPipe
  ],
  providers: [provideIcons({ lucideArrowLeftRight, lucideSearch, lucideShieldAlert })]
})
export class MarketsComponent {
  private readonly instruments = inject(InstrumentSearchService);
  private readonly history = inject(HistoryService);

  protected readonly periods: { value: HistoryPeriod; label: string }[] = [
    { value: '1d', label: '1D' },
    { value: '7d', label: '7D' },
    { value: '1m', label: '1M' },
    { value: '1y', label: '1Y' }
  ];

  /** Markets is open to every role, but only Traders can place orders. */
  protected readonly canTrade = inject(AuthService).getRole() === 'TRADER';
  protected readonly preferences = inject(PreferencesService);

  protected readonly query = signal('');
  protected readonly results = signal<PriceQuoteResult[]>([]);
  protected readonly selected = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('ticker') ?? WATCHLIST[0]);
  protected readonly period = signal<HistoryPeriod>('7d');
  protected readonly points = signal<PricePoint[]>([]);
  protected readonly chartLoading = signal(true);
  protected readonly riskSnapshotExpanded = signal(true);

  protected readonly selectedQuote = computed(() => this.results().find(q => q.ticker === this.selected()));

  /** Change from the first to the last price in the chosen period. */
  protected readonly change = computed(() => {
    const points = this.points();
    if (points.length < 2) {
      return null;
    }
    const first = points[0].value;
    const last = points[points.length - 1].value;
    return { amount: last - first, ratio: (last - first) / first };
  });

  protected readonly riskAssessment = computed(() => {
    const values = this.points().map(point => point.value).filter(value => Number.isFinite(value) && value > 0);
    if (values.length < 3) {
      return null;
    }

    const returns = values.slice(1).map((value, index) => value / values[index] - 1);
    const averageReturn = returns.reduce((total, value) => total + value, 0) / returns.length;
    const variance = returns.reduce((total, value) => total + (value - averageReturn) ** 2, 0) / returns.length;
    const volatility = Math.sqrt(variance) * 100;
    let peak = values[0];
    let maxDrawdown = 0;
    for (const value of values) {
      peak = Math.max(peak, value);
      maxDrawdown = Math.max(maxDrawdown, (peak - value) / peak * 100);
    }

    const first = values[0];
    const last = values[values.length - 1];
    const momentum = (last / first - 1) * 100;
    const riskScore = Math.round(Math.min(100, volatility * 3 + maxDrawdown * 0.8));
    const successEstimate = Math.round(Math.max(5, Math.min(95, 50 + momentum * 1.5 - riskScore * 0.2)));
    const riskLabel = riskScore >= 70 ? 'High' : riskScore >= 40 ? 'Medium' : 'Lower';
    const dataConfidence = values.length >= 30 ? 'Moderate' : 'Limited';

    return { volatility, maxDrawdown, momentum, riskScore, riskLabel, successEstimate, dataConfidence, observations: values.length };
  });

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Search results; an empty box shows the watchlist. Refreshes with the live quote generator.
    combineLatest([
      toObservable(this.query).pipe(debounceTime(300), distinctUntilChanged()),
      interval(PRICE_REFRESH_MS).pipe(startWith(0))
    ]).pipe(
      switchMap(([query]) => query.trim()
        ? this.instruments.searchInstrumentPriceQuote(query.trim()).pipe(catchError(() => of([])))
        : this.instruments.getQuotes(WATCHLIST)),
      takeUntilDestroyed(destroyRef)
    ).subscribe(results => this.results.set(results));

    // Price history for the selected instrument and period
    combineLatest([toObservable(this.selected), toObservable(this.period)]).pipe(
      switchMap(([ticker, period]) => {
        this.chartLoading.set(true);
        return this.history.getPriceHistory(ticker, period).pipe(catchError(() => of([])));
      }),
      takeUntilDestroyed(destroyRef)
    ).subscribe(prices => {
      // The API returns newest first; the chart reads left to right
      this.points.set(prices.map(p => ({ time: p.quoteTimestamp, value: p.price })).reverse());
      this.chartLoading.set(false);
    });
  }
}
