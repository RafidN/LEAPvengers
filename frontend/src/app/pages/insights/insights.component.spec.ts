import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { InsightsComponent } from './insights.component';
import { ClientSegmentResult, ClientSegmentService } from '../../services/client-segment.service';
import { InstrumentInsightsResult, InstrumentInsightsService } from '../../services/instrument-insights.service';

class MockClientSegmentService {
  readonly calls: Array<{ lookbackDays?: number }> = [];

  getClientSegments(request: { lookbackDays?: number }) {
    this.calls.push(request);
    return of(CLIENTS);
  }
}

class MockInstrumentInsightsService {
  readonly calls: Array<{ lookbackDays?: number; segment?: string; limit?: number }> = [];

  getInstrumentInsights(request: { lookbackDays?: number; segment?: string; limit?: number }) {
    this.calls.push(request);
    return of(INSTRUMENTS);
  }
}

const CLIENTS: ClientSegmentResult[] = [
  {
    clientId: 11,
    firstName: 'Alex',
    lastName: 'Taylor',
    email: 'alex@example.com',
    totalPortfolioValue: 150000,
    recentFilledOrderCount: 14,
    segment: 'Active'
  },
  {
    clientId: 22,
    firstName: 'Morgan',
    lastName: 'Lee',
    email: 'morgan@example.com',
    totalPortfolioValue: 260000,
    recentFilledOrderCount: 15,
    segment: 'Premier'
  }
];

const INSTRUMENTS: InstrumentInsightsResult[] = [
  {
    ticker: 'AAPL',
    instrumentName: 'Apple Inc.',
    assetClass: 'Equity',
    currentPositionValue: 250000,
    recentFilledOrderCount: 18,
    buyCount: 10,
    sellCount: 8,
    tradedClientCount: 6
  }
];

describe('InsightsComponent', () => {
    const flush = async () => {
      await Promise.resolve();
      await Promise.resolve();
    };

  let fixture: ComponentFixture<InsightsComponent>;
  let component: InsightsComponent;
  let clientSegmentService: MockClientSegmentService;
  let instrumentInsightsService: MockInstrumentInsightsService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InsightsComponent],
      providers: [
        { provide: ClientSegmentService, useClass: MockClientSegmentService },
        { provide: InstrumentInsightsService, useClass: MockInstrumentInsightsService }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InsightsComponent);
    component = fixture.componentInstance;
    clientSegmentService = TestBed.inject(ClientSegmentService) as unknown as MockClientSegmentService;
    instrumentInsightsService = TestBed.inject(InstrumentInsightsService) as unknown as MockInstrumentInsightsService;
  });

  it('loads client and instrument insights on init with default lookback', async () => {
    await flush();

    expect(clientSegmentService.calls[0]).toEqual({ lookbackDays: 90 });
    expect(instrumentInsightsService.calls[0]).toEqual({
      lookbackDays: 90,
      segment: undefined,
      limit: 10
    });
    expect((component as any).clients()).toEqual(CLIENTS);
    expect((component as any).instruments()).toEqual(INSTRUMENTS);
    expect((component as any).loading()).toBe(false);
  });

  it('reloads both slices when lookback changes', async () => {
    await flush();

    (component as any).lookbackDays.set(30);
    await flush();

    expect(clientSegmentService.calls[1]).toEqual({ lookbackDays: 30 });
    expect(instrumentInsightsService.calls[1]).toEqual({
      lookbackDays: 30,
      segment: undefined,
      limit: 10
    });
  });

  it('applies instrument segment filter without changing client segment request shape', async () => {
    await flush();

    (component as any).selectedInstrumentSegment.set('Active');
    await flush();

    expect(clientSegmentService.calls[1]).toEqual({ lookbackDays: 90 });
    expect(instrumentInsightsService.calls[1]).toEqual({
      lookbackDays: 90,
      segment: 'Active',
      limit: 10
    });
  });
});
