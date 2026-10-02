import { Component, computed, input } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration } from 'chart.js';

export interface PricePoint {
  time: string;   // ISO timestamp
  value: number;
}

/** Line chart of prices over time. Green when the period ended up, red when it ended down. */
@Component({
  selector: 'app-price-chart',
  imports: [BaseChartDirective],
  template: `
    <div class="relative h-72 w-full">
      <canvas baseChart type="line" [data]="data()" [options]="options"></canvas>
    </div>
  `
})
export class PriceChartComponent {
  readonly points = input.required<PricePoint[]>();
  readonly label = input('Price');

  protected readonly data = computed<ChartConfiguration<'line'>['data']>(() => {
    const points = this.points();
    const up = points.length < 2 || points[points.length - 1].value >= points[0].value;
    const colour = up ? '#0f6a46' : '#8f2d2d';

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
        borderColor: colour,
        backgroundColor: up ? 'rgba(15, 106, 70, 0.12)' : 'rgba(143, 45, 45, 0.12)',
        borderWidth: 2,
        fill: true,
        tension: 0.25,
        pointRadius: 0
      }]
    };
  });

  protected readonly options: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    animation: false,
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: { callbacks: { title: items => new Date(this.points()[items[0].dataIndex].time).toLocaleString() } }
    },
    scales: {
      x: { ticks: { maxTicksLimit: 6, maxRotation: 0 }, grid: { display: false } },
      y: { ticks: { maxTicksLimit: 6 } }
    }
  };
}
