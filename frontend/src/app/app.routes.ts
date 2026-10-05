import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { roleGuard } from './guards/role.guard';
import { ShellComponent } from './layout/shell/shell.component';
import { SignInComponent } from './pages/authentication/sign-in/sign-in.component';
import { RegisterComponent } from './pages/authentication/register/register.component';
import { ForgotPasswordComponent } from './pages/authentication/forgot-password/forgot-password.component';

export const routes: Routes = [
  { path: '', redirectTo: 'sign-in', pathMatch: 'full' },
  { path: 'sign-in', component: SignInComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },

  // Signed-in pages share the top navigation in ShellComponent.
  // data.roles lists who may open each page (see roleGuard); no roles means everyone.
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [roleGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent), data: { roles: ['TRADER'] } },
      { path: 'history', loadComponent: () => import('./pages/history/history.component').then(m => m.HistoryComponent), data: { roles: ['TRADER'] } },
      { path: 'markets', loadComponent: () => import('./pages/markets/markets.component').then(m => m.MarketsComponent) },
      { path: 'clients', loadComponent: () => import('./pages/clients/clients.component').then(m => m.ClientsComponent), data: { roles: ['OPS'] } },
      { path: 'insights', loadComponent: () => import('./pages/insights/insights.component').then(m => m.InsightsComponent), data: { roles: ['OPS', 'ANALYST'] } },
      { path: 'access', loadComponent: () => import('./pages/access/access.component').then(m => m.AccessComponent) }
    ]
  },

  { path: '**', redirectTo: 'sign-in' }
];
