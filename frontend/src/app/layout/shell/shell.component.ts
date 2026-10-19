import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import {
  lucideArrowLeftRight, lucideChartColumn, lucideChartLine, lucideChartPie, lucideHistory, lucideLayoutDashboard, lucideLogOut,
  lucideMoon, lucideShield, lucideSun, lucideUsers
} from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { AuthService, ROLE_LABEL, Role } from '../../services/auth.service';
import { ThemeService } from '../../services/theme.service';

interface NavLink {
  path: string;
  label: string;
  icon: string;
  roles: Role[];
}

// Keep roles in step with data.roles in app.routes.ts
const NAV_LINKS: NavLink[] = [
  { path: '/dashboard', label: 'Portfolio', icon: 'lucideLayoutDashboard', roles: ['TRADER'] },
  { path: '/trade', label: 'Trade', icon: 'lucideArrowLeftRight', roles: ['TRADER'] },
  { path: '/history', label: 'History', icon: 'lucideHistory', roles: ['TRADER'] },
  { path: '/clients', label: 'Clients', icon: 'lucideUsers', roles: ['OPS'] },
  { path: '/insights', label: 'Insights', icon: 'lucideChartPie', roles: ['ANALYST'] },
  { path: '/reports', label: 'Reports', icon: 'lucideChartColumn', roles: ['ANALYST', 'OPS'] },
  { path: '/markets', label: 'Markets', icon: 'lucideChartLine', roles: ['TRADER', 'OPS', 'ANALYST'] },
  { path: '/free-money', label: 'Free Money', icon: 'lucideShield', roles: ['TRADER'] }
];

/** Layout for every signed-in page: top navigation (only the current role's pages) plus the page itself. */
@Component({
  selector: 'app-shell',
  templateUrl: './shell.component.html',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, HlmBadgeImports, HlmButtonImports],
  providers: [provideIcons({
    lucideArrowLeftRight, lucideChartColumn, lucideChartLine, lucideChartPie, lucideHistory, lucideLayoutDashboard, lucideLogOut,
    lucideMoon, lucideShield, lucideSun, lucideUsers
  })]
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly themeService = inject(ThemeService);

  protected get firstName(): string {
    return this.authService.getFirstName() || this.authService.getUsername() || '';
  }

  protected get role(): Role | null {
    return this.authService.getRole();
  }

  protected get roleLabel(): string {
    const role = this.authService.getRole();
    return role ? ROLE_LABEL[role] : '';
  }

  protected get homePage(): string {
    return this.authService.homePage();
  }

  protected get links() {
    const role = this.authService.getRole();
    return NAV_LINKS.filter(link => role && link.roles.includes(role));
  }

  protected signOut(): void {
    this.authService.logout();
    this.router.navigate(['/sign-in']);
  }
}
