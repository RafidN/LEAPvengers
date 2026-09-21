import { Routes } from '@angular/router';
import { SignInComponent } from './pages/authentication/sign-in/sign-in.component';
import { RegisterComponent } from './pages/authentication/register/register.component';
import { ForgotPasswordComponent } from './pages/authentication/forgot-password/forgot-password.component';

export const routes: Routes = [
  { path: 'sign-in', component: SignInComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'forgot-password', component: ForgotPasswordComponent },
  { path: '', redirectTo: 'sign-in', pathMatch: 'full' }

];
