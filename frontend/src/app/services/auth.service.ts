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
  clientId: number;
  email: string;
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

  isLoggedIn(): boolean {
    return !!this.getToken();
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

  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/reset-password`, { token, newPassword });
  }

  private storeSession(response: AuthenticationResponse): void {
    if (!this.isBrowser) {
      return;
    }
    localStorage.setItem('authToken', response.token);
    localStorage.setItem('userId', response.userId.toString());
    localStorage.setItem('username', response.username);
    localStorage.setItem('clientId', response.clientId.toString());
    localStorage.setItem('email', response.email);
  }
}
