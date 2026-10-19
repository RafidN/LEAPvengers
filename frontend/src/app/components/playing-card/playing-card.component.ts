import { Component, computed, input } from '@angular/core';

const SUITS: Record<string, { symbol: string; name: string; red: boolean }> = {
  H: { symbol: '♥', name: 'hearts', red: true },
  D: { symbol: '♦', name: 'diamonds', red: true },
  S: { symbol: '♠', name: 'spades', red: false },
  C: { symbol: '♣', name: 'clubs', red: false }
};

const RANK_NAMES: Record<string, string> = { A: 'ace', J: 'jack', Q: 'queen', K: 'king' };

/**
 * One playing card, e.g. card="10H". With no card it shows the back, as the dealer's face-down hole card.
 * Cards are white with dark or red ink in both themes, like real cards on a table.
 */
@Component({
  selector: 'app-playing-card',
  template: `
    @if (parsed(); as c) {
      <div class="relative h-24 w-16 shrink-0 rounded-lg border border-slate-300 bg-white shadow-md select-none sm:h-28 sm:w-20"
        [class.text-red-600]="c.red" [class.text-slate-900]="!c.red" role="img" [attr.aria-label]="c.label">
        <div class="absolute top-1 left-1.5 text-center text-sm leading-none font-semibold">
          <div>{{ c.rank }}</div><div>{{ c.symbol }}</div>
        </div>
        <div class="absolute inset-0 flex items-center justify-center text-3xl">{{ c.symbol }}</div>
        <div class="absolute right-1.5 bottom-1 rotate-180 text-center text-sm leading-none font-semibold">
          <div>{{ c.rank }}</div><div>{{ c.symbol }}</div>
        </div>
      </div>
    } @else {
      <div class="h-24 w-16 shrink-0 rounded-lg border-2 border-white shadow-md sm:h-28 sm:w-20"
        style="background: repeating-linear-gradient(45deg, #9f1239 0 6px, #be123c 6px 12px)"
        role="img" aria-label="Face-down card"></div>
    }
  `
})
export class PlayingCardComponent {
  /** e.g. "10H", "AS"; null or empty = face down. */
  readonly card = input<string | null>(null);

  protected readonly parsed = computed(() => {
    const value = this.card()?.trim().toUpperCase();
    const suit = value ? SUITS[value.slice(-1)] : undefined;
    const rank = value?.slice(0, -1);
    if (!suit || !rank) {
      return null;
    }
    return { rank, symbol: suit.symbol, red: suit.red, label: `${RANK_NAMES[rank] ?? rank} of ${suit.name}` };
  });
}
