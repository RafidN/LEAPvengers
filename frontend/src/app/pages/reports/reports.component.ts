import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { DOCUMENT, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { DisplayCurrencyPipe } from '../../shared/display-currency.pipe';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, combineLatest, forkJoin, interval, map, of, startWith, switchMap } from 'rxjs';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideDatabase, lucideDownload } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ActivityBar, ActivityChartComponent } from '../../components/activity-chart/activity-chart.component';
import { CHART_PROVIDERS } from '../../components/chart-theme';
import { ActivityReport, OrderHealthReport, ReportGrouping, ReportingService } from '../../services/reporting.service';

/** The reporting store is brought up to date every minute, so re-asking more often gains nothing. */
const REFRESH_MS = 60_000;

const GROUPINGS: { value: ReportGrouping; label: string; column: string; overTime: boolean }[] = [
  { value: 'day', label: 'Day', column: 'Day', overTime: true },
  { value: 'week', label: 'Week', column: 'Week', overTime: true },
  { value: 'month', label: 'Month', column: 'Month', overTime: true },
  { value: 'instrument', label: 'Instrument', column: 'Instrument', overTime: false },
  { value: 'assetClass', label: 'Asset class', column: 'Asset class', overTime: false },
  { value: 'segment', label: 'Segment', column: 'Client segment', overTime: false }
];

const RANGES = [
  { days: 7, label: '7D' },
  { days: 30, label: '30D' },
  { days: 90, label: '90D' },
  { days: 365, label: '1Y' }
];

const SEGMENTS = ['Premier', 'Active', 'Core', 'Dormant'];

/** yyyy-MM-dd in local time, as the date inputs and the API use. */
function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** A period's start date (yyyy-MM-dd key) as a short axis label; other groupings keep their label. */
function shortLabel(key: string, label: string, grouping: ReportGrouping): string {
  if (grouping !== 'day' && grouping !== 'week' && grouping !== 'month') {
    return label;
  }
  const [year, month, day] = key.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  return grouping === 'month'
    ? date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' })
    : date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return isoDate(date);
}

function weekStart(dateText: string): string {
  const date = new Date(dateText);
  const day = date.getDay();
  const diff = day === 0 ? 6 : day - 1;
  date.setDate(date.getDate() - diff);
  return isoDate(date);
}

type TrendPoint = {
  label: string;
  successRate: number;
  failureRate: number;
  totalOrders: number;
};

/**
 * Trading activity over time, by instrument, asset class or client segment, for Analysts and Ops (BR-16).
 * Served from the reporting store; clients are only ever counted, never named.
 */
@Component({
  selector: 'app-reports',
  templateUrl: './reports.component.html',
  imports: [
    DisplayCurrencyPipe, DatePipe, DecimalPipe, PercentPipe, FormsModule, NgIcon, BaseChartDirective, ActivityChartComponent,
    HlmAlertImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports, HlmTableImports
  ],
  // The order health trend is drawn here with Chart.js directly, so the page registers it like the chart components
  // do; without this the trend only appears if another chart happened to register Chart.js first
  providers: [CHART_PROVIDERS, provideIcons({ lucideDatabase, lucideDownload })]
})
export class ReportsComponent {
  private readonly reporting = inject(ReportingService);
  private readonly document = inject(DOCUMENT);

  protected readonly groupings = GROUPINGS;
  protected readonly ranges = RANGES;
  protected readonly segments = SEGMENTS;

  /** The last 30 days by default, the same as the backend's default for both reports. */
  protected readonly from = signal(daysAgo(29));
  protected readonly to = signal(isoDate(new Date()));
  protected readonly groupBy = signal<ReportGrouping>('day');
  protected readonly segment = signal('');

  protected readonly report = signal<ActivityReport | null>(null);
  protected readonly orderHealth = signal<OrderHealthReport | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly orderHealthError = signal('');

  protected readonly grouping = computed(() => GROUPINGS.find(g => g.value === this.groupBy())!);

  /** Which preset (if any) the current range matches, to highlight its button. */
  protected readonly activeRange = computed(() =>
    this.to() === isoDate(new Date()) ? RANGES.find(r => this.from() === daysAgo(r.days - 1))?.days ?? null : null);

  /** Chart bars. Periods get short axis labels ("Oct 12", "Oct 2026"); the table keeps the full ones. */
  protected readonly bars = computed<ActivityBar[]>(() => {
    const grouping = this.groupBy();
    return (this.report()?.rows ?? []).map(r => ({ label: shortLabel(r.key, r.label, grouping), value: r.tradedValue, trades: r.trades }));
  });

  protected readonly orderHealthTrendPoints = computed<TrendPoint[]>(() => {
    const rates = this.orderHealth()?.dailyRates ?? [];
    if (!rates.length) {
      return [];
    }

    // For long windows, aggregate by week to reduce day-to-day noise.
    if (rates.length > 45) {
      const buckets = new Map<string, { filled: number; failed: number; total: number }>();
      for (const row of rates) {
        const key = weekStart(row.date);
        const existing = buckets.get(key) ?? { filled: 0, failed: 0, total: 0 };
        existing.filled += row.filledOrders;
        existing.failed += row.rejectedOrders + row.failedOrders;
        existing.total += row.totalOrders;
        buckets.set(key, existing);
      }

      return [...buckets.entries()].map(([key, value]) => {
        const total = value.total;
        const date = new Date(key);
        return {
          label: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
          successRate: total ? (value.filled / total) * 100 : 0,
          failureRate: total ? (value.failed / total) * 100 : 0,
          totalOrders: total
        };
      });
    }

    // For short windows, use trailing 7-day weighted rates for smoother trend lines.
    return rates.map((_, index) => {
      const start = Math.max(0, index - 6);
      let filled = 0;
      let failed = 0;
      let total = 0;
      for (let i = start; i <= index; i += 1) {
        filled += rates[i].filledOrders;
        failed += rates[i].rejectedOrders + rates[i].failedOrders;
        total += rates[i].totalOrders;
      }
      const date = new Date(rates[index].date);
      return {
        label: date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' }),
        successRate: total ? (filled / total) * 100 : 0,
        failureRate: total ? (failed / total) * 100 : 0,
        totalOrders: total
      };
    });
  });

  protected readonly orderHealthTrendLabel = computed(() =>
    (this.orderHealth()?.dailyRates.length ?? 0) > 45
      ? 'Weekly success and failure rates over the selected range'
      : 'Smoothed daily success and failure rates (7-day trailing)'
  );

  protected readonly orderHealthTrendData = computed<ChartConfiguration<'line'>['data']>(() => {
    const points = this.orderHealthTrendPoints();
    return {
      labels: points.map(p => p.label),
      datasets: [
        {
          label: 'Success rate',
          data: points.map(p => Number(p.successRate.toFixed(2))),
          borderColor: '#0f6a46',
          backgroundColor: 'rgba(15,106,70,0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 0
        },
        {
          label: 'Failure rate',
          data: points.map(p => Number(p.failureRate.toFixed(2))),
          borderColor: '#8b1e1e',
          backgroundColor: 'rgba(139,30,30,0.15)',
          borderWidth: 2,
          tension: 0.35,
          pointRadius: 0
        }
      ]
    };
  });

  protected readonly orderHealthTrendOptions = computed<ChartConfiguration<'line'>['options']>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    plugins: {
      legend: { display: true, position: 'bottom' },
      tooltip: {
        callbacks: {
          label: item => ` ${item.dataset.label}: ${Number(item.raw).toFixed(2)}%`,
          afterBody: items => {
            const first = items[0];
            if (!first) {
              return '';
            }
            const point = this.orderHealthTrendPoints()[first.dataIndex];
            return point ? `Orders in window: ${point.totalOrders.toLocaleString('en-US')}` : '';
          }
        }
      }
    },
    scales: {
      y: {
        beginAtZero: true,
        max: 100,
        ticks: {
          callback: value => `${value}%`
        }
      },
      x: {
        ticks: { maxTicksLimit: 10 }
      }
    }
  }));

  constructor() {
    const query = computed(() => ({ from: this.from(), to: this.to(), groupBy: this.groupBy(), segment: this.segment() }));
    combineLatest([toObservable(query), interval(REFRESH_MS).pipe(startWith(0))]).pipe(
      map(([q]) => q),
      switchMap(q => {
        if (!q.from || !q.to) {
          return of({
            activity: null as ActivityReport | null,
            orderHealth: null as OrderHealthReport | null,
            activityError: '',
            orderHealthError: ''
          });
        }
        this.loading.set(true);
        return forkJoin({
          activity: this.reporting.getActivity(q).pipe(
            map(activity => ({ activity, error: '' })),
            catchError((e: HttpErrorResponse) => of({
              activity: null,
              error: e.status === 400 && e.error?.message ? e.error.message : 'Could not load trading activity. Is the backend running?'
            }))
          ),
          orderHealth: this.reporting.getOrderHealth({ from: q.from, to: q.to }).pipe(
            map(orderHealth => ({ orderHealth, error: '' })),
            catchError((e: HttpErrorResponse) => of({
              orderHealth: null,
              error: e.status === 400 && e.error?.message ? e.error.message : 'Could not load order health.'
            }))
          )
        }).pipe(
          map(({ activity, orderHealth }) => ({
            activity: activity.activity,
            orderHealth: orderHealth.orderHealth,
            activityError: activity.error,
            orderHealthError: orderHealth.error
          }))
        );
      }),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(result => {
      this.loading.set(false);
      this.report.set(result.activity);
      this.orderHealth.set(result.orderHealth);
      this.error.set(result.activityError);
      this.orderHealthError.set(result.orderHealthError);
    });
  }

  protected setRange(days: number): void {
    this.from.set(daysAgo(days - 1));
    this.to.set(isoDate(new Date()));
  }

  /** The report as a CSV file, e.g. for a monthly pack. */
  protected downloadCsv(): void {
    const report = this.report();
    if (!report) {
      return;
    }
    const cell = (value: string | number) => {
      const text = String(value);
      return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
    };
    const lines = [
      [this.grouping().column, 'Trades', 'Buys', 'Sells', 'Traded value (USD)', 'Active clients'],
      ...[...report.rows, report.totals].map(r =>
        [r.label, r.trades, r.buys, r.sells, r.tradedValue.toFixed(2), r.activeClients])
    ].map(row => row.map(cell).join(','));
    const segmentPart = report.segment ? `_${report.segment}` : '';
    const blob = new Blob([lines.join('\n') + '\n'], { type: 'text/csv;charset=utf-8' });
    const link = this.document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `trading-activity_by-${report.groupBy}${segmentPart}_${report.from}_to_${report.to}.csv`;
    link.click();
    URL.revokeObjectURL(link.href);
  }
}
