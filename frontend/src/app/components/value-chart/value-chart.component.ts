import { Component, computed, inject, input } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, Plugin } from 'chart.js';
import { AccountValuePoint } from '../../services/account.service';
import { ThemeService } from '../../services/theme.service';
import { CHART_PALETTES, CHART_PROVIDERS } from '../chart-theme';
import { formatMoney } from '../../services/format';

const money = formatMoney;

/** A compact axis label: $98.5k, $1.2M. */
const axisMoney = (value: number) =>
  '$' + value.toLocaleString('en-US', { notation: 'compact', maximumFractionDigits: 1 });

const percent = (ratio: number) =>
  `${ratio >= 0 ? '+' : '−'}${Math.abs(ratio).toLocaleString('en-US', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/** A hairline at the hovered point, so the tooltip reads as a crosshair. */
const crosshair: Plugin<'line'> = {
  id: 'crosshair',
  afterDatasetsDraw(chart) {
    const active = chart.tooltip?.getActiveElements();
    if (!active?.length) {
      return;
    }
    const { ctx, chartArea } = chart;
    const x = active[0].element.x;
    ctx.save();
    ctx.strokeStyle = String(chart.options.color);
    ctx.globalAlpha = 0.5;
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(x, chartArea.top);
    ctx.lineTo(x, chartArea.bottom);
    ctx.stroke();
    ctx.restore();
  }
};

/**
 * Total account value over time as a single line. The y-axis fits the data rather than starting at
 * zero, so there is no area fill (a fill would exaggerate the changes). Hovering shows cash and holdings.
 * When masked (the trader has hidden their balance) the axis has no amounts and hovering shows only
 * the change since the start of the period.
 */
@Component({
  selector: 'app-value-chart',
  providers: [CHART_PROVIDERS],
  imports: [BaseChartDirective],
  template: `
    <div class="relative h-64 w-full">
      <!-- Rebuilt when the theme or masking changes: Chart.js only reads its options when created -->
      @for (key of [renderKey()]; track key) {
        <canvas baseChart type="line" role="img" [attr.aria-label]="summary()"
          [data]="data()" [options]="options()" [plugins]="plugins"></canvas>
      }
    </div>
  `
})
export class ValueChartComponent {
  private readonly theme = inject(ThemeService).theme;

  readonly points = input.required<AccountValuePoint[]>();
  readonly masked = input(false);

  protected readonly plugins = [crosshair];
  private readonly palette = computed(() => CHART_PALETTES[this.theme()]);
  protected readonly renderKey = computed(() => `${this.theme()}:${this.masked()}`);

  /** Times for a single day, dates for anything longer. */
  private readonly labelFormat = computed<Intl.DateTimeFormatOptions>(() => {
    const points = this.points();
    const spanMs = points.length ? Date.parse(points[points.length - 1].time) - Date.parse(points[0].time) : 0;
    return spanMs > 36 * 3600_000 ? { month: 'short', day: 'numeric' } : { hour: '2-digit', minute: '2-digit' };
  });

  protected readonly summary = computed(() => {
    const points = this.points();
    if (!points.length) {
      return 'Account value chart, no data';
    }
    const first = points[0].totalValue;
    const last = points[points.length - 1].totalValue;
    return this.masked() || !first
      ? `Account value changed ${first ? percent((last - first) / first) : ''} over this period`
      : `Account value went from ${money(first)} to ${money(last)}`;
  });

  protected readonly data = computed<ChartConfiguration<'line'>['data']>(() => {
    const palette = this.palette();
    return {
      labels: this.points().map(p => new Date(p.time).toLocaleString([], this.labelFormat())),
      datasets: [{
        data: this.points().map(p => p.totalValue),
        label: 'Total value',
        borderColor: palette.series1,
        borderWidth: 2,
        fill: false,
        tension: 0,
        pointRadius: 0,
        pointHoverRadius: 5,
        pointHoverBackgroundColor: palette.series1,
        pointHoverBorderColor: palette.surface,
        pointHoverBorderWidth: 2
      }]
    };
  });

  protected readonly options = computed<ChartConfiguration<'line'>['options']>(() => {
    const palette = this.palette();
    const masked = this.masked();
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      color: palette.inkMuted,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: palette.surface,
          titleColor: palette.ink,
          bodyColor: palette.ink,
          borderColor: palette.border,
          borderWidth: 1,
          padding: 10,
          displayColors: false,
          callbacks: {
            // A hidden balance shows only the change since the start: green for a gain, red for a loss
            labelTextColor: item => {
              const first = this.points()[0]?.totalValue ?? 0;
              if (!masked || !first) {
                return palette.ink;
              }
              return this.points()[item.dataIndex].totalValue >= first ? palette.upText : palette.downText;
            },
            title: items => new Date(this.points()[items[0].dataIndex].time)
              .toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }),
            label: item => {
              const p = this.points()[item.dataIndex];
              if (masked) {
                const first = this.points()[0].totalValue;
                return first ? `${percent((p.totalValue - first) / first)} since the start` : '';
              }
              return [`Total  ${money(p.totalValue)}`, `Cash  ${money(p.cash)}`, `Holdings  ${money(p.holdingsValue)}`];
            }
          }
        }
      },
      scales: {
        x: {
          border: { display: false },
          grid: { display: false },
          ticks: { color: palette.inkMuted, maxTicksLimit: 6, maxRotation: 0 }
        },
        y: {
          border: { display: false },
          grid: { color: palette.grid },
          ticks: { display: !masked, color: palette.inkMuted, maxTicksLimit: 5, callback: value => axisMoney(Number(value)) }
        }
      }
    };
  });
}
