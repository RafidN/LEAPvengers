import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { catchError, interval, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideExternalLink, lucideTrendingDown, lucideTrendingUp } from '@ng-icons/lucide';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { MOVERS_REFRESH_MS, MarketMovers, MarketService } from '../../services/market.service';

type Side = 'gainers' | 'losers';

/** Today's top US gainers and losers from Yahoo Finance, refreshed every minute. */
@Component({
  selector: 'app-market-movers',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, NgIcon, HlmButtonImports, HlmCardImports, HlmTableImports],
  providers: [provideIcons({ lucideExternalLink, lucideTrendingDown, lucideTrendingUp })],
  template: `
    <section hlmCard class="h-full" aria-labelledby="movers-title">
      <div hlmCardHeader>
        <h2 hlmCardTitle id="movers-title">Top movers</h2>
        <p hlmCardDescription>
          US stocks@if (movers(); as m) { · {{ m.source }}, {{ m.asOf | date: 'HH:mm' }}}
        </p>
        <div hlmCardAction class="flex gap-1" role="group" aria-label="Gainers or losers">
          <button hlmBtn size="sm" [variant]="side() === 'gainers' ? 'secondary' : 'ghost'"
            [attr.aria-pressed]="side() === 'gainers'" (click)="side.set('gainers')">
            <ng-icon name="lucideTrendingUp" [style.color]="'var(--profit)'" />
            Gainers
          </button>
          <button hlmBtn size="sm" [variant]="side() === 'losers' ? 'secondary' : 'ghost'"
            [attr.aria-pressed]="side() === 'losers'" (click)="side.set('losers')">
            <ng-icon name="lucideTrendingDown" [style.color]="'var(--loss)'" />
            Losers
          </button>
        </div>
      </div>
      <div hlmCardContent>
        @if (rows().length) {
          <div hlmTableContainer>
            <table hlmTable>
              <thead hlmTableHeader>
                <tr hlmTableRow>
                  <th hlmTableHead>Stock</th>
                  <th hlmTableHead class="text-right">Price</th>
                  <th hlmTableHead class="text-right">Change</th>
                </tr>
              </thead>
              <tbody hlmTableBody>
                @for (m of rows(); track m.ticker) {
                  <tr hlmTableRow>
                    <td hlmTableCell>
                      <a class="group inline-flex items-center gap-1 font-medium hover:underline" target="_blank" rel="noopener noreferrer"
                        [href]="'https://finance.yahoo.com/quote/' + m.ticker + '/'" [attr.aria-label]="m.ticker + ' on Yahoo Finance (opens in a new tab)'">
                        {{ m.ticker }}
                        <ng-icon name="lucideExternalLink" class="text-muted-foreground text-xs opacity-0 group-hover:opacity-100" />
                      </a>
                      <div class="text-muted-foreground max-w-44 truncate text-xs">{{ m.name }}</div>
                    </td>
                    <td hlmTableCell class="text-right tabular-nums">{{ m.price | currency }}</td>
                    <td hlmTableCell class="text-right font-medium tabular-nums"
                      [style.color]="m.changePercent >= 0 ? 'var(--profit)' : 'var(--loss)'">
                      {{ m.changePercent >= 0 ? '+' : '−' }}{{ abs(m.changePercent) | number: '1.2-2' }}%
                      <div class="text-xs font-normal">
                        {{ m.change >= 0 ? '+' : '−' }}{{ abs(m.change) | currency }}
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        } @else {
          <div class="text-muted-foreground flex h-48 items-center justify-center rounded-lg border border-dashed text-sm">
            {{ error() ? 'Top movers are unavailable right now.' : 'Loading top movers…' }}
          </div>
        }
      </div>
    </section>
  `
})
export class MarketMoversComponent {
  /** How many of each list to show; the source lists 25. */
  private static readonly SHOWN = 8;

  protected readonly side = signal<Side>('gainers');
  protected readonly movers = signal<MarketMovers | null>(null);
  protected readonly error = signal(false);

  protected readonly rows = computed(() => this.movers()?.[this.side()].slice(0, MarketMoversComponent.SHOWN) ?? []);

  constructor() {
    const market = inject(MarketService);
    interval(MOVERS_REFRESH_MS).pipe(
      startWith(0),
      switchMap(() => market.getMovers().pipe(catchError(() => of(null)))),
      takeUntilDestroyed()
    ).subscribe(movers => {
      // A failed refresh keeps the last list on screen
      if (movers) {
        this.movers.set(movers);
      }
      this.error.set(!movers && !this.movers());
    });
  }

  protected abs(value: number): number {
    return Math.abs(value);
  }
}
