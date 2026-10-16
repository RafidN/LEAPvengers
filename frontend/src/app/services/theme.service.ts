/**
 * Light or dark theme. Puts .dark on <html> (which switches the tokens in styles.css) and remembers the
 * choice in localStorage under 'theme'. Without a saved choice it follows the system setting.
 * index.html applies the saved theme before Angular starts, so the page doesn't flash.
 */
import { DOCUMENT, Injectable, PLATFORM_ID, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Chart } from 'chart.js';
import { CHART_PALETTES } from '../components/chart-theme';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'theme';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  readonly theme = signal<Theme>(this.initialTheme());

  constructor() {
    effect(() => this.apply(this.theme()));
  }

  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    if (this.isBrowser) {
      try {
        localStorage.setItem(STORAGE_KEY, next);
      } catch {
        // Storage can be blocked; the theme still applies for this visit
      }
    }
  }

  private initialTheme(): Theme {
    if (!this.isBrowser) {
      return 'light';
    }
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') {
        return saved;
      }
    } catch {
      // Fall through to the system setting
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private apply(theme: Theme): void {
    this.document.documentElement.classList.toggle('dark', theme === 'dark');
    // Axis labels and gridlines of every chart; charts are rebuilt on a theme change to pick these up
    const palette = CHART_PALETTES[theme];
    Chart.defaults.color = palette.inkMuted;
    Chart.defaults.borderColor = palette.grid;
  }
}
