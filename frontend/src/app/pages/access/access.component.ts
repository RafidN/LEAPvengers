import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, map, of } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleCheck, lucideRefreshCw, lucideShieldCheck, lucideShieldX, lucideTriangleAlert } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { AuthService, ROLE_LABEL, Role } from '../../services/auth.service';

interface AccessCheck {
  label: string;
  method: 'GET' | 'POST';
  path: string;
  body?: object;
  allowed: Role[];
  /** The URL is protected by a role rule but no controller exists yet, so only the role check is tested. */
  notBuilt?: boolean;
}

interface AccessResult extends AccessCheck {
  status: number;
  passed: boolean;      // got past the backend's role check
  expected: boolean;    // matches what this role should be able to do
}

const CHECKS: AccessCheck[] = [
  { label: 'Market prices', method: 'POST', path: '/api/search/instrument-price-quote', body: { query: 'NFLX' }, allowed: ['TRADER', 'OPS', 'ANALYST'] },
  { label: 'My accounts and cash', method: 'GET', path: '/api/accounts', allowed: ['TRADER'] },
  { label: 'My order history', method: 'GET', path: '/api/history/orders?period=1m', allowed: ['TRADER', 'OPS', 'ANALYST'] },
  { label: 'Client segments', method: 'POST', path: '/api/clients/segments', body: {}, allowed: ['OPS', 'ANALYST'] },
  { label: 'Ops audit trail', method: 'GET', path: '/api/internal/audit/orders', allowed: ['OPS'], notBuilt: true },
  { label: 'Analyst reports', method: 'GET', path: '/api/internal/reports/activity', allowed: ['ANALYST'], notBuilt: true }
];

/**
 * Asks the real backend what the signed-in role can reach. The frontend hides pages by role,
 * but this shows the server itself refusing (401/403) whatever the role shouldn't see.
 */
@Component({
  selector: 'app-access',
  templateUrl: './access.component.html',
  imports: [DatePipe, NgIcon, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmTableImports],
  providers: [provideIcons({ lucideCircleCheck, lucideRefreshCw, lucideShieldCheck, lucideShieldX, lucideTriangleAlert })]
})
export class AccessComponent {
  private readonly http = inject(HttpClient);
  private readonly authService = inject(AuthService);

  protected readonly role = this.authService.getRole();
  protected readonly roleLabel = this.role ? ROLE_LABEL[this.role] : '';
  protected readonly claims = this.readClaims();
  protected readonly results = signal<AccessResult[]>([]);
  protected readonly running = signal(false);

  constructor() {
    this.run();
  }

  protected run(): void {
    this.running.set(true);
    forkJoin(CHECKS.map(check =>
      this.http.request(check.method, check.path, { body: check.body, observe: 'response' }).pipe(
        map(response => response.status),
        catchError(error => of(error.status as number))
      ).pipe(map(status => {
        const passed = status !== 401 && status !== 403;
        const shouldPass = !!this.role && check.allowed.includes(this.role);
        return { ...check, status, passed, expected: passed === shouldPass };
      }))
    )).subscribe(results => {
      this.results.set(results);
      this.running.set(false);
    });
  }

  protected allowedLabels(roles: Role[]): string {
    return roles.map(r => ROLE_LABEL[r]).join(', ');
  }

  /** The JWT payload, decoded only for display. The backend verifies the signature. */
  private readClaims(): { role?: string; userId?: number; clientId?: number; exp?: number; iat?: number } | null {
    const token = this.authService.getToken();
    try {
      return token ? JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))) : null;
    } catch {
      return null;
    }
  }
}
