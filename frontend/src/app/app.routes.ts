import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { roleGuard } from './guards/role.guard';
import { ShellComponent } from './layout/shell/shell.component';
import { SignInComponent } from './pages/authentication/sign-in/sign-in.component';
import { RegisterComponent } from './pages/authentication/register/register.component';
import { ForgotPasswordComponent } from './pages/authentication/forgot-password/forgot-password.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { HistoryComponent } from './pages/history/history.component';
import { MarketsComponent } from './pages/markets/markets.component';
import { ClientsComponent } from './pages/clients/clients.component';
import { InsightsComponent } from './pages/insights/insights.component';
import { AccessComponent } from './pages/access/access.component';

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
      { path: 'dashboard', component: DashboardComponent, data: { roles: ['TRADER'] } },
      { path: 'history', component: HistoryComponent, data: { roles: ['TRADER'] } },
      { path: 'markets', component: MarketsComponent },
      { path: 'clients', component: ClientsComponent, data: { roles: ['OPS'] } },
      { path: 'insights', component: InsightsComponent, data: { roles: ['OPS', 'ANALYST'] } },
      { path: 'access', component: AccessComponent }
    ]
  },

  { path: '**', redirectTo: 'sign-in' }
];
