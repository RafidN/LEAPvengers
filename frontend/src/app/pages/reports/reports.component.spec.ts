import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ReportsComponent } from './reports.component';
import { ActivityReport, OrderHealthReport, ReportingService } from '../../services/reporting.service';

class MockReportingService {
  readonly activityCalls: Array<{ from: string; to: string; groupBy: string; segment?: string }> = [];
  readonly orderHealthCalls: Array<{ from: string; to: string }> = [];

  getActivity(query: { from: string; to: string; groupBy: string; segment?: string }) {
    this.activityCalls.push(query);
    return of(ACTIVITY_REPORT);
  }

  getOrderHealth(query: { from: string; to: string }) {
    this.orderHealthCalls.push(query);
    return of(ORDER_HEALTH);
  }
}

const ACTIVITY_REPORT: ActivityReport = {
  from: '2026-07-23',
  to: '2026-10-20',
  groupBy: 'day',
  rows: [],
  totals: { key: 'total', label: 'Total', trades: 0, buys: 0, sells: 0, tradedValue: 0, activeClients: 0 }
};

const ORDER_HEALTH: OrderHealthReport = {
  from: '2026-07-23',
  to: '2026-10-20',
  totalOrders: 100,
  submittedOrders: 5,
  acceptedOrders: 10,
  filledOrders: 70,
  rejectedOrders: 10,
  failedOrders: 5,
  successRate: 0.7,
  failureRate: 0.15,
  statusCounts: [
    { status: 'SUBMITTED', count: 5 },
    { status: 'ACCEPTED', count: 10 },
    { status: 'FILLED', count: 70 },
    { status: 'REJECTED', count: 10 },
    { status: 'FAILED', count: 5 }
  ],
  dailyRates: [
    {
      date: '2026-10-19',
      totalOrders: 50,
      filledOrders: 36,
      rejectedOrders: 10,
      failedOrders: 4,
      successRate: 0.72,
      failureRate: 0.28
    },
    {
      date: '2026-10-20',
      totalOrders: 50,
      filledOrders: 34,
      rejectedOrders: 10,
      failedOrders: 1,
      successRate: 0.68,
      failureRate: 0.22
    }
  ]
};

function isoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return isoDate(date);
}

describe('ReportsComponent', () => {
  let fixture: ComponentFixture<ReportsComponent>;
  let component: ReportsComponent;
  let reporting: MockReportingService;

  const flush = async () => {
    await fixture.whenStable();
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsComponent],
      providers: [{ provide: ReportingService, useClass: MockReportingService }]
    }).compileComponents();

    fixture = TestBed.createComponent(ReportsComponent);
    component = fixture.componentInstance;
    reporting = TestBed.inject(ReportingService) as unknown as MockReportingService;
  });

  it('uses a 90-day default range and loads activity plus order health', async () => {
    await flush();

    const expectedFrom = daysAgo(89);
    const expectedTo = isoDate(new Date());

    expect(reporting.activityCalls[0]).toEqual({ from: expectedFrom, to: expectedTo, groupBy: 'day', segment: '' });
    expect(reporting.orderHealthCalls[0]).toEqual({ from: expectedFrom, to: expectedTo });
    expect((component as any).orderHealth()).toEqual(ORDER_HEALTH);
  });

  it('refreshes both report calls when range preset changes', async () => {
    await flush();

    (component as any).setRange(7);
    await flush();

    const expectedFrom = daysAgo(6);
    const expectedTo = isoDate(new Date());

    expect(reporting.activityCalls[1]).toEqual({ from: expectedFrom, to: expectedTo, groupBy: 'day', segment: '' });
    expect(reporting.orderHealthCalls[1]).toEqual({ from: expectedFrom, to: expectedTo });
  });
});