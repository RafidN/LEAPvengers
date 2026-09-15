import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  styleUrls: ['./register.component.css'],
  templateUrl: './register.component.html',
  imports: [CommonModule, FormsModule, RouterModule],
  standalone: true
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
  passwordMismatch = false;
  isLoading = false;
  errorMessage = '';
  successMessage = '';

  onSubmit(event: Event) {
    event.preventDefault();
    
    // Validation
    if (!this.firstName || !this.lastName || !this.email || !this.username || !this.password || !this.confirmPassword) {
      this.errorMessage = 'Please fill in all fields';
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.passwordMismatch = true;
      this.errorMessage = 'Passwords do not match';
      return;
    }

    if (!this.agreeToTerms) {
      this.errorMessage = 'You must agree to the terms and conditions';
      return;
    }

    // Validate email format
    if (!this.isValidEmail(this.email)) {
      this.errorMessage = 'Please enter a valid email address';
      return;
    }

    this.passwordMismatch = false;
    this.errorMessage = '';
    this.successMessage = '';
    this.isLoading = true;

    this.authService.register(this.firstName, this.lastName, this.email, this.username, this.password).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.successMessage = 'Account created successfully! Redirecting to dashboard...';
        console.log('Registration successful:', response);
        setTimeout(() => {
          this.router.navigate(['/landing']);
        }, 1500);
      },
      error: (error) => {
        this.isLoading = false;
        console.error('Registration failed:', error);
        this.errorMessage = error.error?.message || 'Registration failed. Please try again.';
      }
    });
  }

  onPasswordChange() {
    this.passwordMismatch = false;
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }
}
