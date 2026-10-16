import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DOCUMENT, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, combineLatest, interval, map, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideDatabase, lucideDownload } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { ActivityBar, ActivityChartComponent } from '../../components/activity-chart/activity-chart.component';
import { ActivityReport, ReportGrouping, ReportingService } from '../../services/reporting.service';

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

/**
 * Trading activity over time, by instrument, asset class or client segment, for Analysts and Ops (BR-16).
 * Served from the reporting store; clients are only ever counted, never named.
 */
@Component({
  selector: 'app-reports',
  templateUrl: './reports.component.html',
  imports: [
    CurrencyPipe, DatePipe, DecimalPipe, FormsModule, NgIcon, ActivityChartComponent,
    HlmAlertImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports, HlmTableImports
  ],
  providers: [provideIcons({ lucideDatabase, lucideDownload })]
})
export class ReportsComponent {
  private readonly reporting = inject(ReportingService);
  private readonly document = inject(DOCUMENT);

  protected readonly groupings = GROUPINGS;
  protected readonly ranges = RANGES;
  protected readonly segments = SEGMENTS;

  protected readonly from = signal(daysAgo(29));
  protected readonly to = signal(isoDate(new Date()));
  protected readonly groupBy = signal<ReportGrouping>('day');
  protected readonly segment = signal('');

  protected readonly report = signal<ActivityReport | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal('');

  protected readonly grouping = computed(() => GROUPINGS.find(g => g.value === this.groupBy())!);

  /** Which preset (if any) the current range matches, to highlight its button. */
  protected readonly activeRange = computed(() =>
    this.to() === isoDate(new Date()) ? RANGES.find(r => this.from() === daysAgo(r.days - 1))?.days ?? null : null);

  /** Chart bars. Periods get short axis labels ("Oct 12", "Oct 2026"); the table keeps the full ones. */
  protected readonly bars = computed<ActivityBar[]>(() => {
    const grouping = this.groupBy();
    return (this.report()?.rows ?? []).map(r => ({ label: shortLabel(r.key, r.label, grouping), value: r.tradedValue, trades: r.trades }));
  });

  constructor() {
    const query = computed(() => ({ from: this.from(), to: this.to(), groupBy: this.groupBy(), segment: this.segment() }));
    combineLatest([toObservable(query), interval(REFRESH_MS).pipe(startWith(0))]).pipe(
      map(([q]) => q),
      switchMap(q => {
        if (!q.from || !q.to) {
          return of(null);
        }
        this.loading.set(true);
        return this.reporting.getActivity(q).pipe(
          catchError((e: HttpErrorResponse) => {
            this.error.set(e.status === 400 && e.error?.message ? e.error.message : 'Could not load the report. Is the backend running?');
            return of(null);
          })
        );
      }),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(report => {
      this.loading.set(false);
      if (report) {
        this.report.set(report);
        this.error.set('');
      }
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
