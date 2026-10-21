import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { PreferencesService } from './preferences.service';

const DISPLAY_RATES: Record<string, number> = { USD: 1, EUR: 0.92, GBP: 0.79, CAD: 1.36, AUD: 1.52 };
const LOCALES: Record<string, string> = { en: 'en-US', es: 'es-ES', fr: 'fr-FR', de: 'de-DE' };

@Injectable({ providedIn: 'root' })
export class DisplayCurrencyService {
  private readonly http = inject(HttpClient);
  private readonly preferences = inject(PreferencesService);
  private readonly rates = signal<Record<string, number>>(DISPLAY_RATES);

  constructor() {
    this.http.get<Record<string, { rate: number }>>('/api/orders/exchange-rates?source=USD').pipe(
      catchError(() => of(null))
    ).subscribe(response => {
      if (response) {
        this.rates.update(current => Object.fromEntries(
          Object.entries(response).map(([currency, quote]) => [currency, quote.rate])
        ));
      }
    });
  }

  format(value: number, digitsInfo = '1.2-2'): string {
    const currency = this.preferences.currency();
    const locale = LOCALES[this.preferences.language()] ?? 'en-US';
    return new Intl.NumberFormat(locale, {
      style: 'currency', currency, minimumFractionDigits: Number(digitsInfo.slice(2, 3)) || 2,
      maximumFractionDigits: Number(digitsInfo.slice(4)) || 2
    }).format(value * (this.rates()[currency] ?? DISPLAY_RATES[currency] ?? 1));
  }
}
