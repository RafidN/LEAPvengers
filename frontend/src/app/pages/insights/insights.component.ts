import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DecimalPipe, PercentPipe } from '@angular/common';
import { catchError, of, switchMap, tap } from 'rxjs';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ClientSegment, ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';

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
  templateUrl: './insights.component.html',
  imports: [
    CurrencyPipe, DecimalPipe, PercentPipe, BaseChartDirective,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports
  ]
})
export class InsightsComponent {
  private readonly segmentService = inject(ClientSegmentService);

  protected readonly segments: ClientSegment[] = ['Premier', 'Active', 'Core', 'Dormant'];
  protected readonly lookbacks = [30, 90, 180, 365];

  protected readonly lookbackDays = signal(90);
  protected readonly clients = signal<ClientSegmentResult[]>([]);
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

  // One series, one hue: no legend needed, the card title names it; the table beside it is the table view
  protected readonly chartData = computed<ChartConfiguration<'bar'>['data']>(() => ({
    labels: this.summary().map(s => s.segment),
    datasets: [{
      data: this.summary().map(s => s.portfolioValue),
      label: 'Portfolio value',
      backgroundColor: '#0f6a46',
      hoverBackgroundColor: '#37b87b',
      borderRadius: 4,
      maxBarThickness: 28
    }]
  }));

  protected readonly chartOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: item => ` ${Number(item.raw).toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })}`
        }
      }
    },
    scales: {
      x: {
        grid: { color: 'rgba(0,0,0,0.06)' },
        ticks: { maxTicksLimit: 5, callback: value => `$${Number(value) / 1000}k` }
      },
      y: { grid: { display: false } }
    }
  };

  constructor() {
    // The backend classifies segments; the lookback window changes what counts as recent activity
    toObservable(this.lookbackDays).pipe(
      tap(() => this.loading.set(true)),
      switchMap(lookbackDays => this.segmentService.getClientSegments({ lookbackDays }).pipe(
        catchError(err => {
          this.error.set(err.status === 403
            ? 'Your role is not allowed to view client insights.'
            : 'Could not load client insights. Is the backend running?');
          return of([] as ClientSegmentResult[]);
        })
      )),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(clients => {
      this.clients.set(clients);
      this.loading.set(false);
    });
  }
}
