import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  template: '<router-outlet />',
  imports: [RouterOutlet]
})
export class AppComponent {
  constructor() {
    // Created here so the theme applies on every page, including before sign-in
    inject(ThemeService);
  }
}
