import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Sends signed-out (or expired) users to /sign-in. Role checks come in S1.4. */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  if (authService.isLoggedIn()) {
    return true;
  }
  authService.logout();
  return inject(Router).createUrlTree(['/sign-in']);
};
