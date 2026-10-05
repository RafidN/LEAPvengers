import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DecimalPipe, PercentPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, combineLatest, debounceTime, distinctUntilChanged, interval, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideSearch } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { PriceChartComponent, PricePoint } from '../../components/price-chart/price-chart.component';
import { HistoryPeriod, HistoryService } from '../../services/history.service';
import { InstrumentSearchService, PriceQuoteResult, WATCHLIST } from '../../services/instrument-search.service';

/** Search instruments and chart their price history. */
@Component({
  selector: 'app-markets',
  templateUrl: './markets.component.html',
  imports: [
    DecimalPipe, PercentPipe, FormsModule, NgIcon, PriceChartComponent,
    HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmTableImports
  ],
  providers: [provideIcons({ lucideSearch })]
})
export class MarketsComponent {
  private readonly instruments = inject(InstrumentSearchService);
  private readonly history = inject(HistoryService);

  protected readonly periods: { value: HistoryPeriod; label: string }[] = [
    { value: 'past-day', label: '1D' },
    { value: 'past-7-days', label: '7D' },
    { value: 'past-month', label: '1M' },
    { value: 'past-year', label: '1Y' }
  ];

  protected readonly query = signal('');
  protected readonly results = signal<PriceQuoteResult[]>([]);
  protected readonly selected = signal(inject(ActivatedRoute).snapshot.queryParamMap.get('ticker') ?? WATCHLIST[0]);
  protected readonly period = signal<HistoryPeriod>('past-7-days');
  protected readonly points = signal<PricePoint[]>([]);
  protected readonly chartLoading = signal(true);

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

  constructor() {
    const destroyRef = inject(DestroyRef);

    // Search results; an empty box shows the watchlist. Refreshes with the live quote generator.
    combineLatest([
      toObservable(this.query).pipe(debounceTime(300), distinctUntilChanged()),
      interval(15_000).pipe(startWith(0))
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
