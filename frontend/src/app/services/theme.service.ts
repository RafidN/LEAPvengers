/**
 * Light or dark theme. Puts .dark on <html> (which switches the tokens in styles.css) and remembers the
 * choice in localStorage under 'theme'. Without a saved choice it follows the system setting.
 * index.html applies the saved theme before Angular starts, so the page doesn't flash.
 */
import { DOCUMENT, Injectable, PLATFORM_ID, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

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
    // Charts take their colours from CHART_PALETTES themselves and are rebuilt when the theme changes
    this.document.documentElement.classList.toggle('dark', theme === 'dark');
  }
}
