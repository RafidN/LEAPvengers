import { Injectable } from '@angular/core';
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

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8081/api/auth';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<AuthenticationResponse> {
    const loginRequest: LoginRequest = { username, password };
    return this.http.post<AuthenticationResponse>(`${this.apiUrl}/login`, loginRequest).pipe(
      tap((response: AuthenticationResponse) => {
        // Store JWT token in localStorage
        if (response.token) {
          localStorage.setItem('authToken', response.token);
          localStorage.setItem('userId', response.userId.toString());
          localStorage.setItem('username', response.username);
          localStorage.setItem('clientId', response.clientId.toString());
          localStorage.setItem('email', response.email);
        }
      })
    );
  }

  register(firstName: string, lastName: string, email: string, username: string, password: string): Observable<AuthenticationResponse> {
    const registerRequest = { firstName, lastName, email, username, password };
    return this.http.post<AuthenticationResponse>(`${this.apiUrl}/register`, registerRequest).pipe(
      tap((response: AuthenticationResponse) => {
        if (response.token) {
          localStorage.setItem('authToken', response.token);
          localStorage.setItem('userId', response.userId.toString());
          localStorage.setItem('username', response.username);
          localStorage.setItem('clientId', response.clientId.toString());
          localStorage.setItem('email', response.email);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem('authToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('clientId');
    localStorage.removeItem('email');
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('authToken');
  }

  getToken(): string | null {
    return localStorage.getItem('authToken');
  }

  getUsername(): string | null {
    return localStorage.getItem('username');
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/forgot-password`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/reset-password`, { token, newPassword });
  }
}
