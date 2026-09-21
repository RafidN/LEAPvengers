import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: '../styles.css',
  imports: [RouterOutlet],
  standalone: true
})
export class AppComponent {
  protected readonly title = signal('S.H.I.E.L.D.');
}
