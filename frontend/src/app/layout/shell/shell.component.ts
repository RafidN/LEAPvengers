import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideChartLine, lucideChartPie, lucideHistory, lucideLayoutDashboard, lucideLogOut, lucideShield,
  lucideShieldCheck, lucideUsers
} from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { AuthService, ROLE_LABEL, Role } from '../../services/auth.service';

interface NavLink {
  path: string;
  label: string;
  icon: string;
  roles: Role[];
}

// Keep roles in step with data.roles in app.routes.ts
const NAV_LINKS: NavLink[] = [
  { path: '/dashboard', label: 'Portfolio', icon: 'lucideLayoutDashboard', roles: ['TRADER'] },
  { path: '/history', label: 'History', icon: 'lucideHistory', roles: ['TRADER'] },
  { path: '/clients', label: 'Clients', icon: 'lucideUsers', roles: ['OPS'] },
  { path: '/insights', label: 'Insights', icon: 'lucideChartPie', roles: ['ANALYST'] },
  { path: '/markets', label: 'Markets', icon: 'lucideChartLine', roles: ['TRADER', 'OPS', 'ANALYST'] },
  { path: '/access', label: 'Access', icon: 'lucideShieldCheck', roles: ['TRADER', 'OPS', 'ANALYST'] }
];

/** Layout for every signed-in page: top navigation (only the current role's pages) plus the page itself. */
@Component({
  selector: 'app-shell',
  templateUrl: './shell.component.html',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, HlmBadgeImports, HlmButtonImports],
  providers: [provideIcons({
    lucideChartLine, lucideChartPie, lucideHistory, lucideLayoutDashboard, lucideLogOut, lucideShield,
    lucideShieldCheck, lucideUsers
  })]
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly username = this.authService.getUsername();
  private readonly role = this.authService.getRole();
  protected readonly roleLabel = this.role ? ROLE_LABEL[this.role] : '';
  protected readonly homePage = this.authService.homePage();
  protected readonly links = NAV_LINKS.filter(link => this.role && link.roles.includes(this.role));

  protected signOut(): void {
    this.authService.logout();
    this.router.navigate(['/sign-in']);
  }
}
