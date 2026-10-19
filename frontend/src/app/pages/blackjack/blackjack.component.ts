import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { PlayingCardComponent } from '../../components/playing-card/playing-card.component';
import { AccountHolding, AccountResponse, AccountService } from '../../services/account.service';
import { BlackjackRoundResult, BlackjackService } from '../../services/blackjack.service';

@Component({
  selector: 'app-blackjack',
  templateUrl: './blackjack.component.html',
  imports: [
    FormsModule,
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    HlmAlertImports,
    HlmBadgeImports,
    HlmButtonImports,
    HlmCardImports,
    PlayingCardComponent
  ]
})
export class BlackjackComponent {
  private readonly accountService = inject(AccountService);
  private readonly blackjackService = inject(BlackjackService);

  protected readonly accounts = signal<AccountResponse[]>([]);
  protected readonly holdings = signal<AccountHolding[]>([]);
  protected readonly selectedAccountId = signal<number | null>(null);
  protected readonly ticker = signal('AAPL');
  protected readonly betQuantity = signal(1);

  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly round = signal<BlackjackRoundResult | null>(null);

  constructor() {
    this.accountService.getAccounts().subscribe({
      next: accounts => {
        this.accounts.set(accounts);
        if (accounts.length > 0) {
          this.onAccountChange(accounts[0].accountId);
        }
      },
      error: () => {
        this.error.set('Could not load trading accounts.');
      }
    });
  }

  protected startRound(): void {
    this.error.set('');
    this.round.set(null);

    const accountId = this.selectedAccountId();
    const ticker = this.ticker().trim().toUpperCase();
    const betQuantity = this.betQuantity();

    if (!accountId) {
      this.error.set('Select an account first.');
      return;
    }
    if (!ticker) {
      this.error.set('Enter a ticker symbol.');
      return;
    }
    if (betQuantity <= 0) {
      this.error.set('Bet quantity must be greater than zero.');
      return;
    }

    this.loading.set(true);
    this.blackjackService.start({ accountId, ticker, betQuantity }).subscribe({
      next: result => {
        this.round.set(result);
        this.loading.set(false);
      },
      error: response => {
        this.error.set(typeof response?.error === 'string' ? response.error : 'Could not start round.');
        this.loading.set(false);
      }
    });
  }

  protected onAccountChange(accountId: number): void {
    this.selectedAccountId.set(accountId);
    this.holdings.set([]);
    this.ticker.set('');
    this.refreshHoldings();
  }

  private refreshHoldings(): void {
    const accountId = this.selectedAccountId();
    if (!accountId) {
      return;
    }

    this.accountService.getPortfolio(accountId).subscribe({
      next: portfolio => {
        const positive = portfolio.holdings.filter(h => h.quantity > 0);
        this.holdings.set(positive);
        if (positive.length === 0) {
          this.ticker.set('');
          return;
        }

        const currentTicker = this.ticker();
        const stillExists = positive.some(h => h.ticker === currentTicker);
        if (!stillExists) {
          this.ticker.set(positive[0].ticker);
        }
      },
      error: () => {
        this.error.set('Could not load holdings for this account.');
      }
    });
  }

  protected hit(): void {
    this.runAction(roundId => this.blackjackService.hit(roundId));
  }

  protected stand(): void {
    this.runAction(roundId => this.blackjackService.stand(roundId));
  }

  protected split(): void {
    this.runAction(roundId => this.blackjackService.split(roundId));
  }

  private runAction(action: (roundId: string) => import('rxjs').Observable<BlackjackRoundResult>): void {
    const current = this.round();
    if (!current?.roundId) {
      this.error.set('Start a round first.');
      return;
    }

    this.error.set('');
    this.loading.set(true);
    action(current.roundId).subscribe({
      next: nextRound => {
        this.round.set(nextRound);
        this.refreshHoldings();
        this.loading.set(false);
      },
      error: response => {
        this.error.set(typeof response?.error === 'string' ? response.error : 'Could not play action.');
        this.loading.set(false);
      }
    });
  }

  protected outcomeVariant(outcome: 'PENDING' | 'WIN' | 'LOSE' | 'PUSH' | 'MIXED'): 'default' | 'destructive' | 'secondary' {
    switch (outcome) {
      case 'WIN':
        return 'default';
      case 'LOSE':
        return 'destructive';
      case 'MIXED':
        return 'secondary';
      default:
        return 'secondary';
    }
  }
}