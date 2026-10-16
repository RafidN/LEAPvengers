import { Component, computed, inject, input } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';
import { ThemeService } from '../../services/theme.service';
import { CHART_PALETTES } from '../chart-theme';
import { formatMoney } from '../../services/format';

export interface PricePoint {
  time: string;   // ISO timestamp
  value: number;
}

/** $1,234.50 style. */
const formatPrice = (value: number | null) => value === null ? '' : formatMoney(value);

/** Line chart of prices over time. Green when the period ended up, red when it ended down. */
@Component({
  selector: 'app-price-chart',
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

  protected readonly data = computed<ChartConfiguration<'line'>['data']>(() => {
    const palette = CHART_PALETTES[this.theme()];
    const points = this.points();
    const up = points.length < 2 || points[points.length - 1].value >= points[0].value;

    // Show times for a single day, dates for anything longer
    const spanMs = points.length ? Date.parse(points[points.length - 1].time) - Date.parse(points[0].time) : 0;
    const format: Intl.DateTimeFormatOptions = spanMs > 36 * 3600_000
      ? { month: 'short', day: 'numeric' }
      : { hour: '2-digit', minute: '2-digit' };

    return {
      labels: points.map(p => new Date(p.time).toLocaleString([], format)),
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
    const palette = CHART_PALETTES[this.theme()];
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
        x: { ticks: { color: palette.inkMuted, maxTicksLimit: 6, maxRotation: 0 }, grid: { display: false } },
        // Steps of at least a cent: prices show 2 decimals, so finer steps would repeat labels (e.g. FX at $1.12)
        y: { grid: { color: palette.grid }, ticks: { color: palette.inkMuted, maxTicksLimit: 6, precision: 2, callback: value => formatPrice(Number(value)) } }
      }
    };
  });
}
