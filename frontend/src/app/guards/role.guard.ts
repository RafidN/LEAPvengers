import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from '../services/auth.service';

/**
 * Lets a route through only for the roles in its `data.roles`. Anyone else is sent to their
 * own landing page. Routes without `data.roles` are open to every signed-in user.
 * This only hides pages; the backend's SecurityConfig is what actually protects the data.
 */
export const roleGuard: CanActivateFn = route => {
  const authService = inject(AuthService);
  const allowed = route.data['roles'] as Role[] | undefined;
  const role = authService.getRole();

  if (!allowed || (role && allowed.includes(role))) {
    return true;
  }
  return inject(Router).createUrlTree([authService.homePage()]);
};
