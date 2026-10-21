import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReportingService } from './reporting.service';

describe('ReportingService', () => {
  let service: ReportingService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });

    service = TestBed.inject(ReportingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('requests order health with from/to query params', () => {
    service.getOrderHealth({ from: '2026-07-23', to: '2026-10-20' }).subscribe();

    const req = httpMock.expectOne('/api/internal/reports/order-health?from=2026-07-23&to=2026-10-20');
    expect(req.request.method).toBe('GET');
    req.flush({
      from: '2026-07-23',
      to: '2026-10-20',
      totalOrders: 0,
      submittedOrders: 0,
      acceptedOrders: 0,
      filledOrders: 0,
      rejectedOrders: 0,
      failedOrders: 0,
      successRate: 0,
      failureRate: 0,
      statusCounts: [],
      dailyRates: []
    });
  });
});