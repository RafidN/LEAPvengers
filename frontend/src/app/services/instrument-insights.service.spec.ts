import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { InstrumentInsightsService } from './instrument-insights.service';
import { AuthService } from './auth.service';

describe('InstrumentInsightsService', () => {
  let service: InstrumentInsightsService;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(InstrumentInsightsService);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('posts the instrument insights request payload', () => {
    service.getInstrumentInsights({ lookbackDays: 90, segment: 'Active', limit: 10 }).subscribe();

    const req = httpMock.expectOne('/api/insights/instruments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ lookbackDays: 90, segment: 'Active', limit: 10 });
    req.flush([]);
  });

  it('logs out on unauthorized responses', () => {
    let logoutCalls = 0;
    authService.logout = () => {
      logoutCalls += 1;
    };

    service.getInstrumentInsights({ lookbackDays: 30 }).subscribe({
      next: () => {
        throw new Error('expected request to fail');
      },
      error: () => undefined
    });

    const req = httpMock.expectOne('/api/insights/instruments');
    req.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(logoutCalls).toBe(1);
  });
});