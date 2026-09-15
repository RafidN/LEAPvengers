import { Routes } from '@angular/router';
import { SignInComponent } from './pages/sign-in/sign-in.component';

export const routes: Routes = [
  { path: 'sign-in', component: SignInComponent },
  { path: '', redirectTo: 'sign-in', pathMatch: 'full' }
];
