import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { AuthService } from '../../../services/auth.service';

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

  onSubmit() {
    // Validation
    if (!this.firstName || !this.lastName || !this.email || !this.username || !this.password || !this.confirmPassword) {
      this.errorMessage.set('Please fill in all fields');
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.errorMessage.set('Passwords do not match');
      return;
    }

    if (!this.agreeToTerms) {
      this.errorMessage.set('You must agree to the terms and conditions');
      return;
    }

    // Validate email format
    if (!this.isValidEmail(this.email)) {
      this.errorMessage.set('Please enter a valid email address');
      return;
    }

    this.errorMessage.set('');
    this.successMessage.set('');
    this.isLoading.set(true);

    this.authService.register(this.firstName, this.lastName, this.email, this.username, this.password).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Account created successfully! Redirecting to dashboard...');
        setTimeout(() => {
          this.router.navigate(['/dashboard']);
        }, 1500);
      },
      error: (error) => {
        this.isLoading.set(false);
        this.errorMessage.set(error.error?.message || 'Registration failed. Please try again.');
      }
    });
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}
