import { Component, input } from '@angular/core';

@Component({
  selector: 'app-playing-card',
  standalone: true,
  template: `
    <div
      class="relative h-24 w-16 rounded-lg border border-slate-300 bg-white shadow-sm select-none"
      [class.text-red-600]="isRed()"
      [class.text-slate-900]="!isRed()"
      [attr.aria-label]="ariaLabel()">
      <div class="absolute left-1.5 top-1 text-xs font-semibold leading-none">
        <div>{{ rankDisplay() }}</div>
        <div>{{ suitSymbol() }}</div>
      </div>

      <div class="absolute inset-0 flex items-center justify-center text-2xl">
        {{ suitSymbol() }}
      </div>

      <div class="absolute bottom-1 right-1.5 rotate-180 text-xs font-semibold leading-none">
        <div>{{ rankDisplay() }}</div>
        <div>{{ suitSymbol() }}</div>
      </div>
    </div>
  `
})
export class PlayingCardComponent {
  readonly card = input.required<string>();

  protected rankDisplay(): string {
    const parsed = this.parse();
    return parsed ? parsed.rank : '?';
  }

  protected suitSymbol(): string {
    const parsed = this.parse();
    if (!parsed) {
      return '?';
    }

    switch (parsed.suit) {
      case 'H':
        return '♥';
      case 'D':
        return '♦';
      case 'S':
        return '♠';
      case 'C':
        return '♣';
      default:
        return '?';
    }
  }

  protected isRed(): boolean {
    const parsed = this.parse();
    return parsed?.suit === 'H' || parsed?.suit === 'D';
  }

  protected ariaLabel(): string {
    const parsed = this.parse();
    if (!parsed) {
      return 'Unknown card';
    }

    const suitName = parsed.suit === 'H' ? 'hearts'
      : parsed.suit === 'D' ? 'diamonds'
      : parsed.suit === 'S' ? 'spades'
      : 'clubs';

    return `${parsed.rank} of ${suitName}`;
  }

  private parse(): { rank: string; suit: string } | null {
    const value = this.card()?.trim().toUpperCase();
    if (!value || value.length < 2) {
      return null;
    }

    const suit = value.slice(-1);
    const rank = value.slice(0, -1);

    if (!['H', 'D', 'S', 'C'].includes(suit)) {
      return null;
    }

    if (!['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'].includes(rank)) {
      return null;
    }

    return { rank, suit };
  }
}
