import { Pipe, PipeTransform, inject } from '@angular/core';
import { DisplayCurrencyService } from '../services/display-currency.service';

@Pipe({ name: 'currency', standalone: true, pure: false })
export class DisplayCurrencyPipe implements PipeTransform {
  private readonly currency = inject(DisplayCurrencyService);

  transform(value: number | null | undefined, _currencyCode?: string, _display?: string, digitsInfo = '1.2-2'): string | null {
    if (value == null) {
      return null;
    }
    return this.currency.format(value, digitsInfo);
  }
}
