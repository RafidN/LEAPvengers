import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-sign-in',
  styleUrls: ['../auth.css'],
  templateUrl: './sign-in.component.html',
  imports: [FormsModule, RouterLink, HlmAlertImports, HlmButtonImports, HlmCardImports, HlmInputImports, HlmLabelImports]
})
export class SignInComponent {
  private router = inject(Router);
  private authService = inject(AuthService);
  
  username = '';
  password = '';
  // Signals, so updates from HTTP callbacks re-render (the app has no zone.js)
  isLoading = signal(false);
  errorMessage = signal('');

  onSubmit() {
    if (!this.username || !this.password) {
      this.errorMessage.set('Please enter both username and password');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set('');
    
    this.authService.login(this.username, this.password).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (error) => {
        this.isLoading.set(false);
        this.errorMessage.set(error.error?.message || 'Login failed. Please check your credentials.');
      }
    });
  }
}
