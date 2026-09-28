import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideChartLine, lucideLayoutDashboard, lucideLogOut, lucideShield, lucideUsers } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { AuthService } from '../../services/auth.service';

/** Layout for every signed-in page: top navigation plus the page itself. */
@Component({
  selector: 'app-shell',
  templateUrl: './shell.component.html',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, HlmButtonImports],
  providers: [provideIcons({ lucideChartLine, lucideLayoutDashboard, lucideLogOut, lucideShield, lucideUsers })]
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly username = this.authService.getUsername();

  protected readonly links = [
    { path: '/dashboard', label: 'Dashboard', icon: 'lucideLayoutDashboard' },
    { path: '/markets', label: 'Markets', icon: 'lucideChartLine' },
    { path: '/clients', label: 'Clients', icon: 'lucideUsers' }
  ];

  protected signOut(): void {
    this.authService.logout();
    this.router.navigate(['/sign-in']);
  }
}
