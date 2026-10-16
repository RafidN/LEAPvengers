import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { AuthService } from '../../../services/auth.service';

// Kept in step with AuthService.validateRegistration on the backend
const MAX_NAME_LENGTH = 100;
const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 72;
const USERNAME = /^[A-Za-z0-9._-]{3,30}$/;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Sign-up for new traders. The backend opens them a funded trading account at the same time. */
@Component({
  selector: 'app-register',
  styleUrls: ['../auth.css'],
  templateUrl: './register.component.html',
  imports: [FormsModule, RouterLink, HlmAlertImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports]
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  firstName = '';
  lastName = '';
  email = '';
  username = '';
  password = '';
  confirmPassword = '';
  agreeToTerms = false;
  // Signals, so updates from HTTP callbacks re-render (the app has no zone.js)
  isLoading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  // Shown under the fields, so the rules are known before submitting
  protected readonly usernameHint = '3 to 30 characters: letters, numbers, dots, dashes or underscores';
  protected readonly passwordHint = 'At least ' + MIN_PASSWORD_LENGTH + ' characters, with a letter and a number';

  onSubmit() {
    const error = this.validate();
    if (error) {
      this.errorMessage.set(error);
      return;
    }

    this.errorMessage.set('');
    this.successMessage.set('');
    this.isLoading.set(true);

    this.authService.register(
      this.firstName.trim(), this.lastName.trim(), this.email.trim(), this.username.trim(), this.password
    ).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Account created and funded. Taking you to your dashboard…');
        setTimeout(() => this.router.navigate(['/dashboard']), 1200);
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.errorMessage.set(error.status === 0
          ? 'Could not reach the server. Please try again in a moment.'
          : error.error?.message || 'Registration failed. Please try again.');
      }
    });
  }

  /** The same rules the backend applies; the first one broken, or '' when the form is fine. */
  private validate(): string {
    const fields = [this.firstName, this.lastName, this.email, this.username, this.password, this.confirmPassword];
    if (fields.some(f => !f.trim())) {
      return 'Please fill in all fields';
    }
    if (this.firstName.trim().length > MAX_NAME_LENGTH || this.lastName.trim().length > MAX_NAME_LENGTH) {
      return 'Names can be at most ' + MAX_NAME_LENGTH + ' characters';
    }
    if (!EMAIL.test(this.email.trim())) {
      return 'Please enter a valid email address';
    }
    if (!USERNAME.test(this.username.trim())) {
      return 'Usernames need ' + this.usernameHint;
    }
    if (this.password.length < MIN_PASSWORD_LENGTH || this.password.length > MAX_PASSWORD_LENGTH
      || !/[A-Za-z]/.test(this.password) || !/[0-9]/.test(this.password)) {
      return 'Passwords need ' + MIN_PASSWORD_LENGTH + ' to ' + MAX_PASSWORD_LENGTH
        + ' characters, including at least one letter and one number';
    }
    if (this.password !== this.confirmPassword) {
      return 'Passwords do not match';
    }
    if (!this.agreeToTerms) {
      return 'You must agree to the terms and conditions';
    }
    return '';
  }
}
