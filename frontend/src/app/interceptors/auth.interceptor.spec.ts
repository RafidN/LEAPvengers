import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { HistoryService } from '../services/history.service';

describe('authInterceptor', () => {
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let historyService: HistoryService;

  beforeEach(() => {
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    historyService = TestBed.inject(HistoryService);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('attaches the bearer token to an outgoing request', () => {
    localStorage.setItem('authToken', 'test-token-123');

    historyService.getOrderHistoryPastYear().subscribe();

    const req = httpMock.expectOne('/api/history/orders/past-year');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token-123');
    req.flush([]);
  });

  it('sends no Authorization header when signed out', () => {
    historyService.getOrderHistoryPastYear().subscribe();

    const req = httpMock.expectOne('/api/history/orders/past-year');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  // Regression test for the S0.3 bug: AuthService wrote the token under
  // 'authToken' while HistoryService read 'jwtToken', so every history
  // request went out as "Bearer null".
  it('carries the token that AuthService actually stored at login', () => {
    authService.login('trader', 'password').subscribe();

    const loginReq = httpMock.expectOne('/api/auth/login');
    loginReq.flush({
      token: 'token-from-login',
      userId: 1,
      username: 'trader',
      clientId: 1,
      email: 'trader@example.com'
    });

    historyService.getOrderHistoryPastYear().subscribe();

    const historyReq = httpMock.expectOne('/api/history/orders/past-year');
    expect(historyReq.request.headers.get('Authorization')).toBe('Bearer token-from-login');
    expect(historyReq.request.headers.get('Authorization')).not.toContain('null');
    historyReq.flush([]);
  });

  it('attaches the token to instrument search requests too', () => {
    localStorage.setItem('authToken', 'search-token');

    TestBed.inject(HistoryService);
    historyService.getPortfolioHistoryToday().subscribe();

    const req = httpMock.expectOne('/api/history/portfolio/today');
    expect(req.request.headers.get('Authorization')).toBe('Bearer search-token');
    req.flush([]);
  });
});
