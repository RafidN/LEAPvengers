import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideSearch } from '@ng-icons/lucide';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmInputImports } from '@spartan-ng/helm/input';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { InstrumentSearchService, PriceQuoteResult, WATCHLIST } from '../../services/instrument-search.service';

@Component({
  selector: 'app-instrument-search-widget',
  imports: [CurrencyPipe, FormsModule, RouterLink, NgIcon, HlmBadgeImports, HlmCardImports, HlmInputImports, HlmTableImports],
  providers: [provideIcons({ lucideSearch })],
  template: `
    <section hlmCard class="h-full" aria-labelledby="instrument-search-title">
      <div hlmCardHeader>
        <h2 hlmCardTitle id="instrument-search-title">Instrument search</h2>
        <p hlmCardDescription>Find a ticker, stock, FX pair, or crypto asset</p>
      </div>
      <div hlmCardContent class="flex flex-col gap-3">
        <div class="relative">
          <ng-icon name="lucideSearch" class="text-muted-foreground absolute top-1/2 left-2.5 -translate-y-1/2" />
          <input hlmInput class="h-9 w-full pl-8" placeholder="e.g. Tata, ETH, GBP" aria-label="Search instruments"
            [ngModel]="query()" (ngModelChange)="query.set($event)" />
        </div>
        <div hlmTableContainer>
          <table hlmTable>
            <thead hlmTableHeader>
              <tr hlmTableRow>
                <th hlmTableHead>Ticker</th>
                <th hlmTableHead>Class</th>
                <th hlmTableHead class="text-right">Price</th>
              </tr>
            </thead>
            <tbody hlmTableBody>
              @for (quote of results(); track quote.ticker) {
                <tr hlmTableRow>
                  <td hlmTableCell>
                    <a class="block hover:underline" routerLink="/markets" [queryParams]="{ ticker: quote.ticker }">
                      <span class="font-medium">{{ quote.ticker }}</span>
                      <span class="text-muted-foreground block max-w-44 truncate text-xs">{{ quote.instrumentName }}</span>
                    </a>
                  </td>
                  <td hlmTableCell><span hlmBadge variant="secondary">{{ quote.assetClass }}</span></td>
                  <td hlmTableCell class="text-right tabular-nums">{{ quote.currentPrice | currency }}</td>
                </tr>
              } @empty {
                <tr hlmTableRow>
                  <td hlmTableCell colspan="3" class="text-muted-foreground py-6 text-center">
                    {{ loading() ? 'Searching…' : 'No matching instruments.' }}
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </div>
    </section>
  `
})
export class InstrumentSearchWidgetComponent {
  private readonly instruments = inject(InstrumentSearchService);
  protected readonly query = signal('');
  protected readonly results = signal<PriceQuoteResult[]>([]);
  protected readonly loading = signal(true);

  constructor() {
    toObservable(this.query).pipe(
      debounceTime(250),
      distinctUntilChanged(),
      startWith(''),
      switchMap(query => {
        this.loading.set(true);
        const request = query.trim() ? this.instruments.searchInstrumentPriceQuote(query) : this.instruments.getQuotes(WATCHLIST);
        return request.pipe(catchError(() => of([])));
      }),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(results => {
      this.results.set(results);
      this.loading.set(false);
    });
  }
}
