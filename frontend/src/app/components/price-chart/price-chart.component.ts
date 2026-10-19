import { Component, computed, inject, input } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { ThemeService } from '../../services/theme.service';
import { chartPalette, CHART_PROVIDERS } from '../chart-theme';
import { formatMoney } from '../../services/format';

export interface PricePoint {
  time: string;   // ISO timestamp
  value: number;
}

/** $1,234.50 style. */
const formatPrice = (value: number | null) => value === null ? '' : formatMoney(value);

/**
 * Line chart of prices over time. Green when the period ended up, red when it ended down. Points are evenly
 * spaced, so hours when the market was closed take no room; the backend samples one price per interval
 * (GET /history/prices), so every trading day gets the same width.
 */
@Component({
  selector: 'app-price-chart',
  providers: [CHART_PROVIDERS],
  imports: [BaseChartDirective],
  template: `
    <div class="relative h-72 w-full">
      <!-- Rebuilt when the theme changes: Chart.js only reads its options when created -->
      @for (key of [theme()]; track key) {
        <canvas baseChart type="line" [data]="data()" [options]="options()"></canvas>
      }
    </div>
  `
})
export class PriceChartComponent {
  protected readonly theme = inject(ThemeService).theme;

  readonly points = input.required<PricePoint[]>();
  readonly label = input('Price');

  /** Times for a single day, dates for anything longer. */
  private readonly labels = computed(() => {
    const points = this.points();
    const spanMs = points.length ? Date.parse(points[points.length - 1].time) - Date.parse(points[0].time) : 0;
    const format: Intl.DateTimeFormatOptions = spanMs > 36 * 3600_000
      ? { month: 'short', day: 'numeric' }
      : { hour: '2-digit', minute: '2-digit' };
    return points.map(p => new Date(p.time).toLocaleString([], format));
  });

  /** A week or so of dates: label each one once, where it starts, rather than repeat it across its points. */
  private readonly fewLabels = computed(() => new Set(this.labels()).size <= 8);

  protected readonly data = computed<ChartConfiguration<'line'>['data']>(() => {
    const palette = chartPalette(this.theme());
    const points = this.points();
    const up = points.length < 2 || points[points.length - 1].value >= points[0].value;

    return {
      labels: this.labels(),
      datasets: [{
        data: points.map(p => p.value),
        label: this.label(),
        borderColor: up ? palette.up : palette.down,
        backgroundColor: up ? palette.upFill : palette.downFill,
        borderWidth: 2,
        fill: true,
        tension: 0.25,
        pointRadius: 0
      }]
    };
  });

  protected readonly options = computed<ChartConfiguration<'line'>['options']>(() => {
    const palette = chartPalette(this.theme());
    const labels = this.labels();
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: palette.surface,
          titleColor: palette.ink,
          bodyColor: palette.ink,
          borderColor: palette.border,
          borderWidth: 1,
          callbacks: {
            title: items => new Date(this.points()[items[0].dataIndex].time).toLocaleString(),
            label: item => `${item.dataset.label}: ${formatPrice(item.parsed.y)}`
          }
        }
      },
      scales: {
        x: {
          grid: { display: false },
          ticks: this.fewLabels()
            ? {
                color: palette.inkMuted, maxRotation: 0, autoSkip: false,
                callback: (_value, index) => index === 0 || labels[index] !== labels[index - 1] ? labels[index] : null
              }
            : { color: palette.inkMuted, maxTicksLimit: 6, maxRotation: 0 }
        },
        // Steps of at least a cent: prices show 2 decimals, so finer steps would repeat labels (e.g. FX at $1.12)
        y: { grid: { color: palette.grid }, ticks: { color: palette.inkMuted, maxTicksLimit: 6, precision: 2, callback: value => formatPrice(Number(value)) } }
      }
    };
  });
}
