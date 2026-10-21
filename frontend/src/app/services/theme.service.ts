/**
 * App theme. Puts one theme class on <html> (tokens in styles.css) and remembers the choice in localStorage.
 * Without a saved choice it follows the system setting for light/dark.
 */
import { DOCUMENT, Injectable, PLATFORM_ID, effect, inject, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'light' | 'dark' | 'pastel' | 'solarized' | 'earthy' | 'bold' | 'vintage';

const STORAGE_KEY = 'theme';
const THEME_ORDER: Theme[] = ['light', 'dark', 'pastel', 'solarized', 'earthy', 'bold', 'vintage'];
const THEME_CLASSES: Theme[] = ['dark', 'pastel', 'solarized', 'earthy', 'bold', 'vintage'];

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

  readonly themes = THEME_ORDER;

  toggle(): void {
    this.setTheme(this.nextTheme());
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    if (this.isBrowser) {
      try {
        localStorage.setItem(STORAGE_KEY, theme);
      } catch {
        // Storage can be blocked; the theme still applies for this visit
      }
    }
  }

  nextTheme(): Theme {
    const current = this.theme();
    const index = THEME_ORDER.indexOf(current);
    return THEME_ORDER[(index + 1) % THEME_ORDER.length];
  }

  themeLabel(theme: Theme): string {
    const labels: Record<Theme, string> = {
      light: 'Light',
      dark: 'Dark',
      pastel: 'Pastel',
      solarized: 'Solarized',
      earthy: 'Earthy',
      bold: 'Bold',
      vintage: 'Vintage'
    };
    return labels[theme];
  }

  private initialTheme(): Theme {
    if (!this.isBrowser) {
      return 'light';
    }
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved && THEME_ORDER.includes(saved as Theme)) {
        return saved as Theme;
      }
    } catch {
      // Fall through to the system setting
    }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private apply(theme: Theme): void {
    // Charts take their colours from CHART_PALETTES themselves and are rebuilt when the theme changes.
    const root = this.document.documentElement;
    root.classList.remove(...THEME_CLASSES);
    if (theme !== 'light') {
      root.classList.add(theme);
    }
  }
}
