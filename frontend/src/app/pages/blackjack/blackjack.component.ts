import { Component, DestroyRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, forkJoin, of, switchMap } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideCircleCheck, lucideCircleX, lucideGraduationCap, lucideLogOut, lucideSpade } from '@ng-icons/lucide';
import { HlmAlertImports } from '@spartan-ng/helm/alert';
import { HlmBadgeImports } from '@spartan-ng/helm/badge';
import { HlmButtonImports } from '@spartan-ng/helm/button';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmLabelImports } from '@spartan-ng/helm/label';
import { PlayingCardComponent } from '../../components/playing-card/playing-card.component';
import { AccountService } from '../../services/account.service';
import { BlackjackAction, BlackjackHand, BlackjackService, BlackjackState, HandOutcome } from '../../services/blackjack.service';

const BET_SIZES = [5, 10, 25, 50, 100, 250];

const ACTIONS: { action: BlackjackAction; label: string; key: string }[] = [
  { action: 'HIT', label: 'Hit', key: 'H' },
  { action: 'STAND', label: 'Stand', key: 'S' },
  { action: 'DOUBLE', label: 'Double', key: 'D' },
  { action: 'SPLIT', label: 'Split', key: 'P' }
];

const OUTCOME_LABEL: Record<HandOutcome, string> = {
  PLAYING: 'Playing', BLACKJACK: 'Blackjack', WIN: 'Win', PUSH: 'Push', LOSE: 'Lose', BUST: 'Bust'
};

const COACH_KEY = 'riskLab.coach';

interface StakeOption {
  ticker: string;
  quantity: number;
  value: number;
}

/**
 * Risk Lab: real blackjack rules with play money, a basic-strategy coach, and a running comparison with the
 * trader's real portfolio over the same minutes. The lesson: even played perfectly, a game with a house edge
 * loses money over time; a market has no house.
 */
@Component({
  selector: 'app-blackjack',
  templateUrl: './blackjack.component.html',
  imports: [
    CurrencyPipe, DecimalPipe, FormsModule, NgIcon, PlayingCardComponent,
    HlmAlertImports, HlmBadgeImports, HlmButtonImports, HlmCardImports, HlmLabelImports
  ],
  providers: [provideIcons({ lucideCircleCheck, lucideCircleX, lucideGraduationCap, lucideLogOut, lucideSpade })]
})
export class BlackjackComponent {
  private readonly blackjack = inject(BlackjackService);
  private readonly accounts = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly betSizes = BET_SIZES;
  protected readonly actions = ACTIONS;

  protected readonly state = signal<BlackjackState | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly stakes = signal<StakeOption[]>([]);
  protected readonly stakeTicker = signal('');
  protected readonly bet = signal(10);
  protected readonly coachOn = signal(this.readCoachSetting());

  protected readonly table = computed(() => this.state()?.table ?? null);
  protected readonly coach = computed(() => this.state()?.coach ?? null);
  protected readonly stats = computed(() => this.state()?.stats ?? null);
  protected readonly reality = computed(() => this.state()?.realityCheck ?? null);

  /** Share of decisions that matched basic strategy, as a whole percent. */
  protected readonly agreement = computed(() => {
    const s = this.stats();
    return s && s.decisions > 0 ? Math.round(100 * s.decisionsByTheBook / s.decisions) : null;
  });

  /** The chips' change against the real portfolio's over the same minutes: the point of the page. */
  protected readonly verdict = computed(() => {
    const s = this.stats();
    const r = this.reality();
    if (!s || !r || s.roundsPlayed === 0) {
      return 'Play a few hands. The scoreboard compares your chips with your real portfolio over the same minutes.';
    }
    if (s.net < 0 && r.portfolioChange >= 0) {
      return 'The table is down; your portfolio isn\'t. Investing 1, blackjack 0.';
    }
    if (s.net < 0) {
      return 'Both are down, but only one of them is guaranteed to stay that way over enough hands.';
    }
    if (s.net > 0) {
      return 'You\'re up. Enjoy it: over enough hands the house edge always wins, which is why casinos have chandeliers.';
    }
    return 'Dead level so far. The house edge is patient.';
  });

  constructor() {
    this.run(this.blackjack.state());
    this.accounts.getAccounts().pipe(
      switchMap(list => list.length ? forkJoin(list.map(a => this.accounts.getPortfolio(a.accountId))) : of([])),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: portfolios => this.stakes.set(portfolios.flatMap(p => p.holdings)
        .filter(h => h.quantity > 0 && h.value != null && h.value >= 5)
        .map(h => ({ ticker: h.ticker, quantity: h.quantity, value: h.value! }))
        .sort((a, b) => b.value - a.value)),
      error: () => this.stakes.set([])
    });
  }

  protected sitDown(): void {
    this.run(this.blackjack.sitDown(this.stakeTicker() || undefined));
  }

  protected leave(): void {
    this.busy.set(true);
    this.blackjack.leave().subscribe({
      next: () => {
        this.state.set({ seated: false });
        this.busy.set(false);
      },
      error: (e: HttpErrorResponse) => this.fail(e)
    });
  }

  protected deal(): void {
    const t = this.table();
    if (!t?.canDeal) {
      return;
    }
    this.run(this.blackjack.deal(Math.min(this.bet(), Math.floor(t.chips))));
  }

  protected act(action: BlackjackAction): void {
    if (this.allowed(action)) {
      this.run(this.blackjack.act(action));
    }
  }

  protected allowed(action: BlackjackAction): boolean {
    const t = this.table();
    if (!t || this.busy()) {
      return false;
    }
    return { HIT: t.canHit, STAND: t.canStand, DOUBLE: t.canDouble, SPLIT: t.canSplit }[action];
  }

  protected toggleCoach(): void {
    this.coachOn.update(on => !on);
    try {
      localStorage.setItem(COACH_KEY, String(this.coachOn()));
    } catch {
      // Storage can be blocked; the setting still applies for this visit
    }
  }

  /** H, S, D and P play; N or Enter deals the next hand. */
  @HostListener('document:keydown', ['$event'])
  protected onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (!this.table() || event.ctrlKey || event.metaKey || event.altKey
      || (target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName))) {
      return;
    }
    const key = event.key.toUpperCase();
    const action = ACTIONS.find(a => a.key === key);
    if (action && this.allowed(action.action)) {
      event.preventDefault();
      this.act(action.action);
    } else if ((key === 'N' || key === 'ENTER') && this.table()?.canDeal && !this.table()?.inRound) {
      event.preventDefault();
      this.deal();
    }
  }

  protected outcomeLabel(hand: BlackjackHand): string {
    return OUTCOME_LABEL[hand.outcome];
  }

  protected outcomeVariant(outcome: HandOutcome): 'default' | 'secondary' | 'destructive' | 'outline' {
    return outcome === 'WIN' || outcome === 'BLACKJACK' ? 'default'
      : outcome === 'LOSE' || outcome === 'BUST' ? 'destructive' : 'secondary';
  }

  protected actionName(action?: BlackjackAction): string {
    return ACTIONS.find(a => a.action === action)?.label.toLowerCase() ?? '';
  }

  private run(request: Observable<BlackjackState>): void {
    this.busy.set(true);
    this.error.set('');
    request.subscribe({
      next: state => {
        this.state.set(state);
        this.busy.set(false);
      },
      error: (e: HttpErrorResponse) => this.fail(e)
    });
  }

  private fail(e: HttpErrorResponse): void {
    this.busy.set(false);
    this.error.set(e.status === 400 && e.error?.message ? e.error.message : 'The table isn\'t answering. Is the backend running?');
  }

  private readCoachSetting(): boolean {
    try {
      return localStorage.getItem(COACH_KEY) !== 'false';
    } catch {
      return true;
    }
  }
}
