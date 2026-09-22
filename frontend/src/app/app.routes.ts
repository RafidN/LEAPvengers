import { Routes } from '@angular/router';
import { SignInComponent } from './pages/sign-in/sign-in.component';
import { RegisterComponent } from './pages/register/register.component';
import { LandingComponent } from './pages/landing/landing.component';
import { ForgotPasswordComponent } from './pages/authentication/forgot-password/forgot-password.component';

export const routes: Routes = [
  { path: 'sign-in', component: SignInComponent },
  { path: 'register', component: RegisterComponent },
  { path: '', redirectTo: 'sign-in', pathMatch: 'full' },
  { path: 'landing', component: LandingComponent }
];
