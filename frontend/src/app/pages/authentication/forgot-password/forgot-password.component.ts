import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  styleUrls: ['./forgot-password.css'],
  templateUrl: './forgot-password.html',
  imports: [CommonModule, FormsModule, RouterModule],
  standalone: true
})
export class ForgotPasswordComponent {
  private authService = inject(AuthService);
  private router = inject(Router);

  username = '';
  email = '';
  isLoading = false;
  errorMessage = '';
  successMessage = '';

  onSubmit(event: Event) {
    event.preventDefault();
    
    // Validation
    if (!this.username) {
      this.errorMessage = 'Please fill in your username';
      return;
    }
    if (!this.email) {
      this.errorMessage = 'Please fill in your email address';
      return;
    }



    // Validate email format
    if (!this.isValidEmail(this.email)) {
      this.errorMessage = 'Please enter a valid email address';
      return;
    }

    this.errorMessage = '';
    this.successMessage = '';
    this.isLoading = true;

    this.authService.forgotPassword(this.username, this.email).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.successMessage = 'Password reset link sent successfully! Please check your email.';
        console.log('Forgot password request successful:', response);
        setTimeout(() => {
          this.router.navigate(['/signin']);
        }, 1500);
      },
      error: (error) => {
        this.isLoading = false;
        console.error('Password reset failed:', error);
        this.errorMessage = error.error?.message || 'Password reset failed. Please try again.';
      }
    });
  }


  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}

