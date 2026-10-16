import { Component, computed, inject, input } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { ThemeService } from '../../services/theme.service';
import { CHART_PALETTES } from '../chart-theme';
import { formatMoney } from '../../services/format';

export interface ActivityBar {
  label: string;
  value: number;      // traded value, USD
  trades: number;
}

/** A compact axis label: $98.5K, $1.2M. */
const axisMoney = (value: number) => '$' + value.toLocaleString('en-US', { notation: 'compact', maximumFractionDigits: 1 });

/**
 * Traded value as bars, one series in one colour. Time runs left to right (vertical bars); categories such as
 * instruments are ranked top to bottom (horizontal bars). The table beside it is the exact-figures view.
 */
@Component({
  selector: 'app-activity-chart',
  imports: [BaseChartDirective],
  template: `
    <div class="relative w-full" [style.height.px]="height()">
      <!-- Rebuilt when the theme or layout changes: Chart.js only reads its options when created -->
      @for (key of [renderKey()]; track key) {
        <canvas baseChart type="bar" role="img" [attr.aria-label]="summary()"
          [data]="data()" [options]="options()"></canvas>
      }
    </div>
  `
})
export class ActivityChartComponent {
  private readonly theme = inject(ThemeService).theme;

  readonly bars = input.required<ActivityBar[]>();
  /** true for time periods (vertical bars), false for ranked categories (horizontal bars). */
  readonly overTime = input(true);

  protected readonly renderKey = computed(() => `${this.theme()}:${this.overTime()}`);

  /** Ranked categories get a row each, so the chart grows with them; time series keep a fixed height. */
  protected readonly height = computed(() => this.overTime() ? 288 : Math.max(160, this.bars().length * 32 + 48));

  protected readonly summary = computed(() => {
    const top = [...this.bars()].sort((a, b) => b.value - a.value)[0];
    return top ? `Traded value chart; highest is ${top.label} at ${formatMoney(top.value)}` : 'Traded value chart, no trading';
  });

  protected readonly data = computed<ChartConfiguration<'bar'>['data']>(() => {
    const palette = CHART_PALETTES[this.theme()];
    return {
      labels: this.bars().map(b => b.label),
      datasets: [{
        data: this.bars().map(b => b.value),
        label: 'Traded value',
        backgroundColor: palette.series1,
        hoverBackgroundColor: palette.series1Hover,
        borderRadius: 4,
        maxBarThickness: 28
      }]
    };
  });

  protected readonly options = computed<ChartConfiguration<'bar'>['options']>(() => {
    const palette = CHART_PALETTES[this.theme()];
    const overTime = this.overTime();
    const valueAxis = {
      grid: { color: palette.grid },
      border: { display: false },
      ticks: { color: palette.inkMuted, maxTicksLimit: 5, callback: (value: string | number) => axisMoney(Number(value)) }
    };
    const labelAxis = {
      grid: { display: false },
      border: { display: false },
      ticks: { color: palette.inkMuted, maxRotation: 0, autoSkip: true, maxTicksLimit: overTime ? 8 : undefined }
    };
    return {
      indexAxis: overTime ? 'x' : 'y',
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
          displayColors: false,
          callbacks: {
            label: item => {
              const bar = this.bars()[item.dataIndex];
              return [`Traded value  ${formatMoney(bar.value)}`, `Trades  ${bar.trades}`];
            }
          }
        }
      },
      scales: overTime ? { x: labelAxis, y: valueAxis } : { x: valueAxis, y: labelAxis }
    };
  });
}
