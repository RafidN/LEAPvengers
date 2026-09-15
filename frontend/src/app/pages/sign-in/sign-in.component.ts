import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-sign-in',
  styleUrls: ['./sign-in.component.css'],
  templateUrl: './sign-in.component.html',
  imports: [RouterModule, FormsModule, CommonModule],
  standalone: true
})
export class SignInComponent {
  private router = inject(Router);
  private authService = inject(AuthService);
  
  username = '';
  password = '';
  rememberMe = false;
  isLoading = false;
  errorMessage = '';

  onSubmit(event: Event) {
    event.preventDefault();
    
    if (!this.username || !this.password) {
      this.errorMessage = 'Please enter both username and password';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    
    this.authService.login(this.username, this.password).subscribe({
      next: (response) => {
        this.isLoading = false;
        console.log('Login successful:', response);
        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        this.isLoading = false;
        console.error('Login failed:', error);
        this.errorMessage = error.error?.message || 'Login failed. Please check your credentials.';
      }
    });
  }
}
