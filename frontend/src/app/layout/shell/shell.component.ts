import { isPlatformBrowser } from '@angular/common';
import { Component, DestroyRef, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { filter, map, startWith, timer } from 'rxjs';
import {
  lucideArrowLeftRight, lucideChartColumn, lucideChartLine, lucideChartPie, lucideHistory, lucideLayoutDashboard, lucideLogOut,
  lucideSettings2, lucideShield, lucideStar, lucideCoins, lucideCircleUserRound, lucideUsers
} from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { AuthService, ROLE_LABEL, Role } from '../../services/auth.service';
import { PreferencesService } from '../../services/preferences.service';

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
  { path: '/markets', label: 'Markets', icon: 'lucideChartLine', roles: ['TRADER', 'OPS', 'ANALYST'] }
];

const MARKET_TIME_ZONE = 'America/New_York';

function marketIsOpen(now = new Date()): boolean {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: MARKET_TIME_ZONE,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(now);

  const weekday = parts.find(part => part.type === 'weekday')?.value;
  const hour = Number(parts.find(part => part.type === 'hour')?.value);
  const minute = Number(parts.find(part => part.type === 'minute')?.value);

  if (!weekday || Number.isNaN(hour) || Number.isNaN(minute)) {
    return false;
  }

  if (weekday === 'Sat' || weekday === 'Sun') {
    return false;
  }

  const minutesSinceMidnight = hour * 60 + minute;
  return minutesSinceMidnight >= 9 * 60 + 30 && minutesSinceMidnight < 16 * 60;
}

/**
 * Layout for every signed-in page: top navigation (only the current role's pages) plus the page itself, in a centred
 * column. A route whose data.fullBleed is true gets the whole window under the navigation instead; 'advanced' gets it
 * only in the advanced mode (Settings, or the Trade page's view toggle), where the Trade page is a full-window chart.
 */
@Component({
  selector: 'app-shell',
  templateUrl: './shell.component.html',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgIcon, HlmBadgeImports, HlmButtonImports],
  providers: [provideIcons({
    lucideArrowLeftRight, lucideChartColumn, lucideChartLine, lucideChartPie, lucideHistory, lucideLayoutDashboard, lucideLogOut,
    lucideSettings2, lucideShield, lucideStar, lucideCoins, lucideCircleUserRound, lucideUsers
  })]
})
export class ShellComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly preferences = inject(PreferencesService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  protected readonly marketOpen = signal(false);

  constructor() {
    if (!this.isBrowser) {
      return;
    }

    this.marketOpen.set(marketIsOpen());
    const initialDelay = 60_000 - (Date.now() % 60_000);
    timer(initialDelay, 60_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.marketOpen.set(marketIsOpen());
    });
  }

  /** The current route's data.fullBleed: true, 'advanced' or absent. */
  private readonly routeFullBleed = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd),
    startWith(null),
    map(() => {
      let route = this.router.routerState.snapshot.root;
      while (route.firstChild) {
        route = route.firstChild;
      }
      return route.data['fullBleed'] as boolean | 'advanced' | undefined;
    })
  ), { initialValue: undefined });

  protected readonly fullBleed = computed(() => {
    const fullBleed = this.routeFullBleed();
    return fullBleed === true || (fullBleed === 'advanced' && this.preferences.mode() === 'advanced');
  });

  protected get firstName(): string {
    return this.preferences.displayName() || this.authService.getFirstName() || this.authService.getUsername() || '';
  }

  protected get profileIconName(): string {
    return {
      user: 'lucideCircleUserRound',
      shield: 'lucideShield',
      star: 'lucideStar',
      money: 'lucideCoins'
    }[this.preferences.profileIcon()];
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
