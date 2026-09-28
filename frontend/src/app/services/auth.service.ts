import { Injectable, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';

interface LoginRequest {
  username: string;
  password: string;
}

interface AuthenticationResponse {
  token: string;
  userId: number;
  username: string;
  clientId: number | null;
  email: string | null;
}

/**
 * Owns the JWT and related session data in localStorage. This is the only
 * service that should read or write localStorage directly - everything else
 * (e.g. the auth interceptor) goes through getToken()/isLoggedIn() here.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = '/api/auth';
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

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
    if (!this.isBrowser) {
      return;
    }
    localStorage.removeItem('authToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('clientId');
    localStorage.removeItem('email');
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

  getUsername(): string | null {
    if (!this.isBrowser) {
      return null;
    }
    return localStorage.getItem('username');
  }

  forgotPassword(username: string, email: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/forgot-password`, { username, email });
  }

  private storeSession(response: AuthenticationResponse): void {
    if (!this.isBrowser) {
      return;
    }
    localStorage.setItem('authToken', response.token);
    localStorage.setItem('userId', response.userId.toString());
    localStorage.setItem('username', response.username);
    // Ops and Analyst users have no client, so clientId and email can be null
    localStorage.setItem('clientId', String(response.clientId ?? ''));
    localStorage.setItem('email', response.email ?? '');
  }
}
