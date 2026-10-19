import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DecimalPipe, PercentPipe } from '@angular/common';
import { catchError, forkJoin, of, switchMap, tap } from 'rxjs';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ClientSegment, ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';
import { InstrumentInsightsResult, InstrumentInsightsService } from '../../services/instrument-insights.service';
import { ThemeService } from '../../services/theme.service';
import { chartPalette, CHART_PROVIDERS } from '../../components/chart-theme';

interface SegmentSummary {
  segment: ClientSegment;
  clients: number;
  portfolioValue: number;
  filledOrders: number;
}

/**
 * Analyst insights: trading activity and portfolio value by client segment.
 * Clients are shown by id only; analysts don't need names or emails (BRS 9.3).
 */
@Component({
  selector: 'app-insights',
  providers: [CHART_PROVIDERS],
  templateUrl: './insights.component.html',
  imports: [
    CurrencyPipe, DecimalPipe, PercentPipe, BaseChartDirective,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports
  ]
})
export class InsightsComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly segmentService = inject(ClientSegmentService);
  private readonly instrumentInsightsService = inject(InstrumentInsightsService);

  protected readonly segments: ClientSegment[] = ['Premier', 'Active', 'Core', 'Dormant'];
  protected readonly lookbacks = [30, 90, 180, 365];

  protected readonly lookbackDays = signal(90);
  protected readonly selectedInstrumentSegment = signal<ClientSegment | null>(null);
  protected readonly clients = signal<ClientSegmentResult[]>([]);
  protected readonly instruments = signal<InstrumentInsightsResult[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly totalValue = computed(() => this.clients().reduce((t, c) => t + c.totalPortfolioValue, 0));
  protected readonly totalOrders = computed(() => this.clients().reduce((t, c) => t + c.recentFilledOrderCount, 0));
  protected readonly activeShare = computed(() => {
    const clients = this.clients();
    return clients.length ? clients.filter(c => c.recentFilledOrderCount > 0).length / clients.length : 0;
  });

  protected readonly summary = computed<SegmentSummary[]>(() => this.segments.map(segment => {
    const inSegment = this.clients().filter(c => c.segment === segment);
    return {
      segment,
      clients: inSegment.length,
      portfolioValue: inSegment.reduce((t, c) => t + c.totalPortfolioValue, 0),
      filledOrders: inSegment.reduce((t, c) => t + c.recentFilledOrderCount, 0)
    };
  }));

  protected readonly topClients = computed(() =>
    [...this.clients()].sort((a, b) => b.recentFilledOrderCount - a.recentFilledOrderCount).slice(0, 5));

  protected readonly topInstruments = computed(() =>
    [...this.instruments()]
      .sort((a, b) => b.currentPositionValue - a.currentPositionValue || b.recentFilledOrderCount - a.recentFilledOrderCount)
      .slice(0, 5));

  protected readonly theme = inject(ThemeService).theme;
  private readonly palette = computed(() => chartPalette(this.theme()));

  // One series, one hue: no legend needed, the card title names it; the table beside it is the table view
  protected readonly chartData = computed<ChartConfiguration<'bar'>['data']>(() => ({
    labels: this.summary().map(s => s.segment),
    datasets: [{
      data: this.summary().map(s => s.portfolioValue),
      label: 'Portfolio value',
      backgroundColor: this.palette().series1,
      hoverBackgroundColor: this.palette().series1Hover,
      borderRadius: 4,
      maxBarThickness: 28
    }]
  }));

  /** Both bar charts share these options; they follow the theme. */
  protected readonly chartOptions = computed<ChartConfiguration<'bar'>['options']>(() => {
    const palette = this.palette();
    return {
      indexAxis: 'y',
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: palette.surface,
          titleColor: palette.ink,
          bodyColor: palette.ink,
          borderColor: palette.border,
          borderWidth: 1,
          callbacks: {
            label: item => ` ${Number(item.raw).toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })}`
          }
        }
      },
      scales: {
        x: {
          grid: { color: palette.grid },
          ticks: { color: palette.inkMuted, maxTicksLimit: 5, callback: value => `$${Number(value) / 1000}k` }
        },
        y: { grid: { display: false }, ticks: { color: palette.inkMuted } }
      }
    };
  });

  protected readonly instrumentChartData = computed<ChartConfiguration<'bar'>['data']>(() => ({
    labels: this.topInstruments().map(item => item.ticker),
    datasets: [{
      data: this.topInstruments().map(item => item.currentPositionValue),
      label: 'Invested value',
      backgroundColor: this.palette().series2,
      hoverBackgroundColor: this.palette().series2Hover,
      borderRadius: 4,
      maxBarThickness: 28
    }]
  }));


  constructor() {
    const insightQuery = computed(() => ({
      lookbackDays: this.lookbackDays(),
      segment: this.selectedInstrumentSegment() ?? undefined
    }));

    // The backend classifies segments; analysts can additionally focus the instrument view by segment.
    toObservable(insightQuery).pipe(
      tap(() => {
        this.loading.set(true);
        this.error.set('');
      }),
      switchMap(({ lookbackDays, segment }) => forkJoin({
        clients: this.segmentService.getClientSegments({ lookbackDays }),
        instruments: this.instrumentInsightsService.getInstrumentInsights({ lookbackDays, segment, limit: 10 })
      }).pipe(
        catchError(err => {
          this.error.set(err.status === 401 || err.status === 403
            ? 'Your role is not allowed to view client insights.'
            : 'Could not load client insights. Is the backend running?');
          return of({
            clients: [] as ClientSegmentResult[],
            instruments: [] as InstrumentInsightsResult[]
          });
        })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ clients, instruments }) => {
      this.clients.set(clients);
      this.instruments.set(instruments);
      this.loading.set(false);
    });
  }
}
