import { Component } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import {ChartConfiguration} from 'chart.js';
@Component({
  imports: [BaseChartDirective],
  selector: 'app-chart',
  templateUrl: './chart.html',
  standalone: true,
})
export class Chart {
  public lineChart: ChartConfiguration<'line'>["data"]={
    labels:['January', 'February', 'March', 'April', 'May', 'June', 'July'],
    datasets:[{data: [0,10250,9800,11000,11500],
      label: 'Portfolio Value',
      borderColor: '#16a34a',
      backgroundColor: 'rgba(22, 163, 74, 0.2)',
      fill: true,
      tension: 0.4,
    },]
  };
  

  public lineChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    plugins:{
      legend: {
        display: true,
        position: 'top',
      },
    }
  };
}
