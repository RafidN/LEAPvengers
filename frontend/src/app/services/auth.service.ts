import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { BalanceVisibilityService } from './balance-visibility.service';

interface LoginRequest {
  username: string;
  password: string;
}

export type Role = 'TRADER' | 'OPS' | 'ANALYST';

/** Where each role lands after signing in, and where a wrong-role URL sends them back to. */
const HOME_PAGE: Record<Role, string> = {
  TRADER: '/dashboard',
  OPS: '/clients',
  ANALYST: '/insights'
};

export const ROLE_LABEL: Record<Role, string> = {
  TRADER: 'Trader',
  OPS: 'Operations',
  ANALYST: 'Analyst'
};

// Ops and Analyst users belong to no client, so the backend leaves clientId and email out
interface AuthenticationResponse {
  token: string;
  userId: number;
  firstName?: string;
  lastName?: string;
  username: string;
  clientId?: number;
  email?: string;
  role: Role;
}

/**
 * Owns the JWT and related session data in localStorage. This is the only
 * service that should read or write session data in localStorage - everything else
 * (e.g. the auth interceptor) goes through getToken()/isLoggedIn() here.
 * (ThemeService keeps the unrelated 'theme' preference there too.)
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth';
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly balanceVisibility = inject(BalanceVisibilityService);

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<AuthenticationResponse> {
    const loginRequest: LoginRequest = { username, password };
    return this.http.post<AuthenticationResponse>(`${this.apiUrl}/login`, loginRequest).pipe(
      tap((response: AuthenticationResponse) => {
        if (response.token) {
          this.storeSession(response);
        }
      })
    );
  }

  register(firstName: string, lastName: string, email: string, username: string, password: string): Observable<AuthenticationResponse> {
    const registerRequest = { firstName, lastName, email, username, password };
    return this.http.post<AuthenticationResponse>(`${this.apiUrl}/register`, registerRequest).pipe(
      tap((response: AuthenticationResponse) => {
        if (response.token) {
          this.storeSession(response);
        }
      })
    );
  }

  logout(): void {
    // Balances start hidden for whoever signs in next
    this.balanceVisibility.hideAll();
    if (!this.isBrowser) {
      return;
    }
    localStorage.removeItem('authToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('firstName');
    localStorage.removeItem('lastName');
    localStorage.removeItem('username');
    localStorage.removeItem('clientId');
    localStorage.removeItem('email');
    localStorage.removeItem('role');
  }

  /** True when a token is stored and hasn't expired yet. */
  isLoggedIn(): boolean {
    const token = this.getToken();
    if (!token) {
      return false;
    }
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.exp !== 'number' || payload.exp * 1000 > Date.now();
    } catch {
      return false;
    }
  }

  getToken(): string | null {
    if (!this.isBrowser) {
      return null;
    }
    return localStorage.getItem('authToken');
  }

  getFirstName(): string | null {
    if (!this.isBrowser) {
      return null;
    }
    return localStorage.getItem('firstName');
  }

  getLastName(): string | null {
    if (!this.isBrowser) {
      return null;
    }
    return localStorage.getItem('lastName');
  }

  getUsername(): string | null {
    if (!this.isBrowser) {
      return null;
    }
    const storedUsername = localStorage.getItem('username');
    if (storedUsername) {
      return storedUsername;
    }

    const token = this.getToken();
    if (!token) {
      return null;
    }

    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.sub === 'string' ? payload.sub : null;
    } catch {
      return null;
    }
  }

  getRole(): Role | null {
    if (!this.isBrowser) {
      return null;
    }
    return localStorage.getItem('role') as Role | null;
  }

  /** The signed-in user's landing page, or /sign-in when nobody is signed in. */
  homePage(): string {
    const role = this.getRole();
    return role ? HOME_PAGE[role] : '/sign-in';
  }

  forgotPassword(username: string, email: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/forgot-password`, { username, email });
  }

  private storeSession(response: AuthenticationResponse): void {
    if (!this.isBrowser) {
      return;
    }
    // Clear first so a trader's clientId doesn't linger when an Ops user signs in after them
    this.logout();
    localStorage.setItem('authToken', response.token);
    localStorage.setItem('userId', response.userId.toString());
    if (response.firstName) {
      localStorage.setItem('firstName', response.firstName);
    }
    if (response.lastName) {
      localStorage.setItem('lastName', response.lastName);
    }
    localStorage.setItem('username', response.username);
    localStorage.setItem('role', response.role);
    if (response.clientId != null) {
      localStorage.setItem('clientId', response.clientId.toString());
    }
    if (response.email) {
      localStorage.setItem('email', response.email);
    }
  }
}
