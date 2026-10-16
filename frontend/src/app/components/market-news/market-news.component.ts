import { Component, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, interval, of, startWith, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideExternalLink } from '@ng-icons/lucide';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { MarketNewsArticle, MarketService, NEWS_REFRESH_MS } from '../../services/market.service';

/** "just now", "12 min ago", "3 h ago", then the date. */
function timeAgo(iso: string | undefined, now: number): string {
  if (!iso) {
    return '';
  }
  const minutes = Math.floor((now - Date.parse(iso)) / 60_000);
  if (minutes < 1) {
    return 'just now';
  }
  if (minutes < 60) {
    return `${minutes} min ago`;
  }
  if (minutes < 24 * 60) {
    return `${Math.floor(minutes / 60)} h ago`;
  }
  return new Date(iso).toLocaleDateString([], { month: 'short', day: 'numeric' });
}

/** Stock market headlines from public news feeds (via the backend), refreshed every 5 minutes. */
@Component({
  selector: 'app-market-news',
  imports: [NgIcon, HlmCardImports],
  providers: [provideIcons({ lucideExternalLink })],
  template: `
    <section hlmCard class="h-full" aria-labelledby="news-title">
      <div hlmCardHeader>
        <h2 hlmCardTitle id="news-title">Market news</h2>
        <p hlmCardDescription>Latest headlines from {{ sources() }}</p>
      </div>
      <div hlmCardContent>
        @if (articles().length) {
          <ul class="divide-border flex flex-col divide-y">
            @for (a of articles().slice(0, limit()); track a.link) {
              <li class="py-3 first:pt-0 last:pb-0">
                <a class="group block" [href]="a.link" target="_blank" rel="noopener noreferrer">
                  <p class="text-muted-foreground mb-0.5 text-xs">
                    <span class="text-foreground font-medium">{{ a.source }}</span>
                    @if (a.publishedAt) { · <time [attr.datetime]="a.publishedAt">{{ ago(a.publishedAt) }}</time> }
                  </p>
                  <p class="font-medium leading-snug group-hover:underline">
                    {{ a.title }}
                    <ng-icon name="lucideExternalLink" class="text-muted-foreground ml-0.5 align-[-2px] text-xs" aria-hidden="true" />
                    <span class="sr-only">(opens in a new tab)</span>
                  </p>
                  @if (a.summary) {
                    <p class="text-muted-foreground mt-1 line-clamp-2 text-sm">{{ a.summary }}</p>
                  }
                </a>
              </li>
            }
          </ul>
        } @else {
          <div class="text-muted-foreground flex h-48 items-center justify-center rounded-lg border border-dashed text-sm">
            {{ error() ? 'News is unavailable right now.' : 'Loading news…' }}
          </div>
        }
      </div>
    </section>
  `
})
export class MarketNewsComponent {
  /** How many headlines to show. */
  readonly limit = input(8);

  protected readonly articles = signal<MarketNewsArticle[]>([]);
  protected readonly error = signal(false);
  private now = Date.now();

  constructor() {
    const market = inject(MarketService);
    interval(NEWS_REFRESH_MS).pipe(
      startWith(0),
      switchMap(() => market.getNews().pipe(catchError(() => of(null)))),
      takeUntilDestroyed()
    ).subscribe(articles => {
      this.now = Date.now();
      // A failed refresh keeps the last headlines on screen
      if (articles) {
        this.articles.set(articles);
      }
      this.error.set(!articles && !this.articles().length);
    });
  }

  /** The publishers in the list, e.g. "CNBC and MarketWatch". */
  protected sources(): string {
    const names = [...new Set(this.articles().map(a => a.source))];
    return names.length ? names.join(' and ') : 'public news feeds';
  }

  protected ago(iso: string): string {
    return timeAgo(iso, this.now);
  }
}
