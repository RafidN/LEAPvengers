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
  selector: 'app-forgot-password',
  styleUrls: ['../auth.css'],
  templateUrl: './forgot-password.html',
  imports: [FormsModule, RouterLink, HlmAlertImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports]
})
export class ForgotPasswordComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  username = '';
  email = '';
  // Signals, so updates from HTTP callbacks re-render (the app has no zone.js)
  isLoading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  onSubmit() {
    // Validation
    if (!this.username) {
      this.errorMessage.set('Please fill in your username');
      return;
    }
    if (!this.email) {
      this.errorMessage.set('Please fill in your email address');
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

    this.authService.forgotPassword(this.username, this.email).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set('Password reset link sent successfully! Please check your email.');
        setTimeout(() => {
          this.router.navigate(['/sign-in']);
        }, 1500);
      },
      error: (error) => {
        this.isLoading.set(false);
        this.errorMessage.set(error.error?.message || 'Password reset failed. Please try again.');
      }
    });
  }


  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}

