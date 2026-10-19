import { Component, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, Plugin } from 'chart.js';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucidePlus, lucideX } from '@ng-icons/lucide';
import { ThemeService } from '../../services/theme.service';
import { CHART_PALETTES, CHART_PROVIDERS } from '../chart-theme';
import { PricePoint } from '../price-chart/price-chart.component';
import {
  ChartGeometry, OrderKind, OrderSide, decimalsOf, orderName, ordersAt, priceStep, priceToY, snap, waitsAt, yToPrice
} from './chart-geometry';

/** A working limit or stop order, drawn as a line at its price. */
export interface ChartOrder {
  orderId: number;
  side: OrderSide;
  kind: OrderKind;
  quantity: number;
  price: number;
}

/** The limit or stop order being set up in the ticket, drawn as a dashed line until it is placed. */
export interface ChartDraft {
  side: OrderSide;
  kind: OrderKind;
  quantity: number | null;
  price: number;
}

/** One line on the chart, as drawn. */
interface Line {
  key: string;
  order: ChartOrder | null;   // null for the draft
  side: OrderSide;
  kind: OrderKind;
  quantity: number | null;
  price: number;
  busy: boolean;
}

/** A line being moved, with the mouse or touch (pointer) or the arrow keys (keyboard). */
interface Drag {
  key: string;
  startY: number;
  price: number;
  moved: boolean;
  keyboard: boolean;
}

/**
 * A price chart to trade from, like TradingView: working limit and stop orders are lines at their price, which can
 * be dragged to a new price or canceled with their ×. Hovering shows the price under the pointer and a + button
 * that starts a limit or stop order there; it appears as a dashed line, which can be dragged too, until it is
 * placed. The chart only draws and reports: the page owning it places, moves and cancels the orders.
 *
 * It fills the height of its container, so the page decides how big it is.
 *
 * Keyboard: each line's label is a button; the arrow keys move it (Shift for bigger steps), Enter places the move,
 * Escape undoes it and Delete cancels the order.
 */
@Component({
  selector: 'app-trading-chart',
  providers: [CHART_PROVIDERS, provideIcons({ lucidePlus, lucideX })],
  imports: [BaseChartDirective, NgIcon],
  host: { class: 'block h-full', '(document:keydown.escape)': 'closeMenu()' },
  template: `
    <div #frame class="relative h-full min-h-48 w-full touch-pan-y select-none"
      (pointermove)="pointerMove($event)" (pointerdown)="pointerDown($event)" (pointerup)="pointerUp()"
      (pointercancel)="cancelDrag()" (pointerleave)="hoverY.set(null)">
      <!-- Rebuilt when the theme changes: Chart.js only reads its colours when created -->
      @for (key of [theme()]; track key) {
        <canvas baseChart type="line" [data]="data()" [options]="options()" [plugins]="plugins"></canvas>
      }

      @if (geometry(); as g) {
        <div class="pointer-events-none absolute inset-0 overflow-hidden text-[11px] leading-4">
          <!-- Price under the pointer, with + to start an order there -->
          @if (crosshair(); as c) {
            <div class="absolute border-t border-dotted" [style.border-color]="'var(--muted-foreground)'"
              [style.top.px]="c.y" [style.left.px]="g.left" [style.width.px]="g.right - g.left"></div>
            <span class="bg-foreground text-background absolute -translate-y-1/2 rounded px-1 tabular-nums"
              [style.top.px]="c.y" [style.left.px]="g.right + 2">{{ format(c.price) }}</span>
            @if (livePrice() != null) {
              <button type="button" data-chart-control
                class="bg-card text-foreground hover:bg-muted pointer-events-auto absolute flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded border shadow-sm"
                [style.top.px]="c.y" [style.left.px]="g.right - 26"
                [attr.aria-label]="'Place an order at ' + format(c.price)" [attr.aria-expanded]="!!menu()"
                (click)="openMenu(c.price, c.y)">
                <ng-icon name="lucidePlus" />
              </button>
            }
          }

          <!-- Market price -->
          @if (livePrice(); as p) {
            <div class="absolute border-t border-dashed" [style.border-color]="'var(--primary)'"
              [style.top.px]="y(p, g)" [style.left.px]="g.left" [style.width.px]="g.right - g.left"></div>
            <span class="bg-primary text-primary-foreground absolute -translate-y-1/2 rounded px-1 font-semibold tabular-nums"
              [style.top.px]="y(p, g)" [style.left.px]="g.right + 2">{{ format(p) }}</span>
          }

          <!-- Working orders and the draft -->
          @for (line of lines(); track line.key) {
            <div class="absolute" [class.opacity-60]="line.busy"
              [style.top.px]="y(line.price, g)" [style.left.px]="g.left" [style.width.px]="g.right - g.left">
              <div class="absolute top-0 w-full border-t-2" [class.border-dashed]="!line.order"
                [style.border-color]="colour(line.side)"></div>
              <!-- A taller strip than the line itself, so it is easy to grab -->
              @if (!line.busy) {
                <div data-chart-control class="pointer-events-auto absolute -top-2 h-4 w-full cursor-ns-resize touch-none"
                  (pointerdown)="startDrag($event, line)"></div>
              }
              <div data-chart-control
                class="bg-card pointer-events-auto absolute top-0 left-2 flex -translate-y-1/2 items-stretch overflow-hidden rounded-md border shadow-sm"
                [style.border-color]="colour(line.side)">
                <button type="button" class="cursor-ns-resize touch-none px-1.5 py-0.5 font-semibold whitespace-nowrap text-white"
                  [style.background-color]="colour(line.side)" [disabled]="line.busy"
                  [attr.aria-label]="describe(line)"
                  (pointerdown)="startDrag($event, line)" (keydown)="keyDown($event, line)" (blur)="keyboardBlur(line)">
                  {{ label(line) }}
                </button>
                <span class="text-foreground px-1.5 py-0.5 tabular-nums">{{ format(line.price) }}</span>
                @if (!line.order) {
                  <button type="button" class="text-foreground hover:bg-muted border-l px-1.5 font-semibold disabled:opacity-50"
                    [disabled]="!canPlaceDraft()" [title]="canPlaceDraft() ? '' : placeHint()"
                    (click)="placeDraft.emit()">Place</button>
                }
                <button type="button" class="text-muted-foreground hover:bg-muted hover:text-foreground border-l px-1"
                  [disabled]="line.busy" [attr.aria-label]="line.order ? 'Cancel order #' + line.order.orderId : 'Discard this order'"
                  (click)="remove(line)">
                  <ng-icon name="lucideX" />
                </button>
              </div>
            </div>
            <span class="absolute -translate-y-1/2 rounded px-1 font-semibold text-white tabular-nums"
              [style.background-color]="colour(line.side)"
              [style.top.px]="y(line.price, g)" [style.left.px]="g.right + 2">{{ format(line.price) }}</span>
          }

          <!-- What the + can start at that price -->
          @if (menu(); as m) {
            <div role="menu" data-chart-control
              class="bg-popover text-popover-foreground pointer-events-auto absolute z-10 flex -translate-x-full flex-col rounded-md border p-1 text-xs shadow-md"
              [style.top.px]="m.y + 12" [style.left.px]="g.right - 4" aria-label="Start an order">
              @for (choice of menuChoices(); track choice.kind + choice.side) {
                <button type="button" role="menuitem" class="hover:bg-muted flex items-center gap-2 rounded px-2 py-1.5 text-left whitespace-nowrap"
                  (click)="choose(choice.side, choice.kind)">
                  <span class="h-2 w-2 shrink-0 rounded-full" [style.background-color]="colour(choice.side)"></span>
                  {{ orderName(choice.side, choice.kind) }} at {{ format(m.price) }}
                  <span class="text-muted-foreground">{{ hint(choice.side, choice.kind) }}</span>
                </button>
              }
            </div>
          }
        </div>
      }
    </div>
  `
})
export class TradingChartComponent {
  protected readonly theme = inject(ThemeService).theme;
  private readonly frame = viewChild.required<ElementRef<HTMLElement>>('frame');

  /** Price history, oldest first; the page appends the live price as it arrives. */
  readonly points = input.required<PricePoint[]>();
  readonly livePrice = input<number | null>(null);
  readonly ticker = input('');
  readonly orders = input<ChartOrder[]>([]);
  readonly draft = input<ChartDraft | null>(null);
  readonly canPlaceDraft = input(false);
  /** Why the draft can't be placed yet, e.g. no quantity; shown on its Place button. */
  readonly placeHint = input('');
  /** Orders being moved or canceled: drawn faded, and can't be dragged meanwhile. */
  readonly busyOrderIds = input<number[]>([]);

  /** The draft dragged to a new price; past the market price a limit order becomes a stop order and back. */
  readonly draftChange = output<{ price: number; kind: OrderKind }>();
  /** An order started from the + menu. */
  readonly stage = output<{ side: OrderSide; kind: OrderKind; price: number }>();
  readonly placeDraft = output<void>();
  readonly discardDraft = output<void>();
  readonly moveOrder = output<{ order: ChartOrder; price: number }>();
  readonly cancelOrder = output<ChartOrder>();

  protected readonly orderName = orderName;
  protected readonly geometry = signal<ChartGeometry | null>(null);
  protected readonly hoverY = signal<number | null>(null);
  protected readonly menu = signal<{ price: number; y: number } | null>(null);
  private readonly drag = signal<Drag | null>(null);
  /** Where a working order was dropped, shown until the page has moved it (or put it back). */
  private readonly dropped = signal(new Map<number, number>());
  /** The prices the axis was stretched to fit when a drag began; kept while it lasts so the scale holds still. */
  private frozenExtras: number[] = [];
  private latestGeometry: ChartGeometry | null = null;

  /** Reads where Chart.js drew the plot after each update, for the lines drawn over it. */
  protected readonly plugins: Plugin<'line'>[] = [{
    id: 'orderLineGeometry',
    afterUpdate: chart => {
      const scale = chart.scales['y'];
      const area = chart.chartArea;
      if (!scale || !area) {
        return;
      }
      const next: ChartGeometry = {
        left: area.left, right: area.right, top: area.top, bottom: area.bottom, min: scale.min, max: scale.max
      };
      const previous = this.latestGeometry;
      if (previous && (Object.keys(next) as (keyof ChartGeometry)[]).every(k => previous[k] === next[k])) {
        return;
      }
      // Set after the chart's own update, which can run during change detection. Compared with and set from the
      // latest update rather than the signal, so a burst of updates (a resize, or a chart rebuilt for a theme
      // change) always ends on the last one's geometry.
      this.latestGeometry = next;
      queueMicrotask(() => this.geometry.set(this.latestGeometry));
    }
  }];

  protected readonly step = computed(() => {
    const g = this.geometry();
    return g ? priceStep(g) : 0.01;
  });

  protected readonly lines = computed<Line[]>(() => {
    const drag = this.drag();
    const busy = new Set(this.busyOrderIds());
    const dropped = this.dropped();
    const lines: Line[] = this.orders().map(order => {
      const key = 'order-' + order.orderId;
      const price = drag?.key === key ? drag.price : busy.has(order.orderId) ? dropped.get(order.orderId) ?? order.price : order.price;
      return { key, order, side: order.side, kind: order.kind, quantity: order.quantity, price, busy: busy.has(order.orderId) };
    });
    const draft = this.draft();
    if (draft) {
      lines.push({ key: 'draft', order: null, side: draft.side, kind: draft.kind, quantity: draft.quantity, price: draft.price, busy: false });
    }
    return lines;
  });

  protected readonly crosshair = computed(() => {
    const g = this.geometry();
    const menu = this.menu();
    if (menu) {
      return menu;
    }
    const y = this.hoverY();
    if (!g || y == null || this.drag() || y < g.top || y > g.bottom) {
      return null;
    }
    return { y, price: snap(yToPrice(y, g), this.step()) };
  });

  protected readonly menuChoices = computed(() => {
    const menu = this.menu();
    const market = this.livePrice();
    return menu && market != null ? ordersAt(menu.price, market) : [];
  });

  /** Prices the axis must show besides the history: the market price and every line. */
  private readonly extras = computed(() => [
    this.livePrice(), ...this.orders().map(o => o.price), this.draft()?.price
  ].filter((p): p is number => p != null));

  private readonly rangeExtras = computed(() => this.drag() ? this.frozenExtras : this.extras());

  /** Times for a single day, dates for anything longer. */
  private readonly labels = computed(() => {
    const points = this.points();
    const spanMs = points.length ? Date.parse(points[points.length - 1].time) - Date.parse(points[0].time) : 0;
    const format: Intl.DateTimeFormatOptions = spanMs > 36 * 3600_000
      ? { month: 'short', day: 'numeric' }
      : { hour: '2-digit', minute: '2-digit' };
    return points.map(p => new Date(p.time).toLocaleString([], format));
  });

  protected readonly data = computed<ChartConfiguration<'line'>['data']>(() => {
    const palette = CHART_PALETTES[this.theme()];
    const points = this.points();
    const up = points.length < 2 || points[points.length - 1].value >= points[0].value;
    return {
      labels: this.labels(),
      datasets: [{
        data: points.map(p => p.value),
        label: this.ticker() || 'Price',
        borderColor: up ? palette.up : palette.down,
        backgroundColor: up ? palette.upFill : palette.downFill,
        borderWidth: 2,
        fill: true,
        tension: 0.2,
        pointRadius: 0
      }]
    };
  });

  protected readonly options = computed<ChartConfiguration<'line'>['options']>(() => {
    const palette = CHART_PALETTES[this.theme()];
    const values = [...this.points().map(p => p.value), ...this.rangeExtras()];
    const low = Math.min(...values);
    const high = Math.max(...values);
    // Room above and below the highest and lowest line, so their labels aren't cut off at the edge
    const pad = (high - low || high * 0.01 || 1) * 0.08;
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: palette.surface,
          titleColor: palette.ink,
          bodyColor: palette.ink,
          borderColor: palette.border,
          borderWidth: 1,
          callbacks: {
            title: items => new Date(this.points()[items[0].dataIndex].time).toLocaleString(),
            label: item => `${item.dataset.label}: ${this.format(item.parsed.y ?? 0)}`
          }
        }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: palette.inkMuted, maxTicksLimit: 6, maxRotation: 0, autoSkipPadding: 24 } },
        // On the right, like trading platforms, where the order and price labels sit
        y: {
          position: 'right',
          suggestedMin: low - pad,
          suggestedMax: high + pad,
          grid: { color: palette.grid },
          ticks: { color: palette.inkMuted, maxTicksLimit: 7, callback: value => this.format(Number(value)) }
        }
      }
    };
  });

  protected y(price: number, g: ChartGeometry): number {
    return priceToY(price, g);
  }

  /** $77,670.00, or more decimals where the chart snaps finer than a cent (FX). */
  protected format(price: number): string {
    const decimals = Math.max(2, decimalsOf(this.step()));
    return price.toLocaleString('en-US', {
      style: 'currency', currency: 'USD', minimumFractionDigits: decimals, maximumFractionDigits: decimals
    });
  }

  protected quantity(value: number): string {
    return value.toLocaleString('en-US', { maximumFractionDigits: 4 });
  }

  protected colour(side: OrderSide): string {
    return side === 'BUY' ? 'var(--buy)' : 'var(--sell)';
  }

  protected name(line: Line): string {
    return orderName(line.side, line.kind);
  }

  /** "Buy limit 0.05" */
  protected label(line: Line): string {
    return line.quantity == null ? this.name(line) : `${this.name(line)} ${this.quantity(line.quantity)}`;
  }

  protected describe(line: Line): string {
    const what = `${this.name(line)}${line.quantity != null ? ' ' + this.quantity(line.quantity) : ''} ${this.ticker()} at ${this.format(line.price)}`;
    return line.order
      ? `${what}. Drag or use the arrow keys to move it, Delete to cancel it.`
      : `${what}, not placed yet. Drag or use the arrow keys to set the price.`;
  }

  /** What each + menu choice does, in a few words. */
  protected hint(side: OrderSide, kind: OrderKind): string {
    if (kind === 'LIMIT') {
      return side === 'BUY' ? 'buy if it dips' : 'take profit';
    }
    return side === 'SELL' ? 'limit a loss' : 'buy a breakout';
  }

  protected openMenu(price: number, y: number): void {
    this.menu.set(this.menu() ? null : { price, y });
  }

  closeMenu(): void {
    this.menu.set(null);
    if (this.drag()) {
      this.cancelDrag();
    }
  }

  protected choose(side: OrderSide, kind: OrderKind): void {
    const menu = this.menu();
    this.menu.set(null);
    if (menu) {
      this.stage.emit({ side, kind, price: menu.price });
    }
  }

  protected remove(line: Line): void {
    if (line.order) {
      this.cancelOrder.emit(line.order);
    } else {
      this.discardDraft.emit();
    }
  }

  // ---- Dragging ----

  protected startDrag(event: PointerEvent, line: Line): void {
    if (line.busy || event.button !== 0) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    (event.target as HTMLElement).setPointerCapture?.(event.pointerId);
    this.frozenExtras = this.extras();
    this.menu.set(null);
    this.drag.set({ key: line.key, startY: this.frameY(event), price: line.price, moved: false, keyboard: false });
  }

  protected pointerMove(event: PointerEvent): void {
    const drag = this.drag();
    const y = this.frameY(event);
    if (!drag || drag.keyboard) {
      if (event.pointerType === 'mouse' && !this.menu()) {
        this.hoverY.set(y);
      }
      return;
    }
    const g = this.geometry();
    if (!g) {
      return;
    }
    const line = this.lines().find(l => l.key === drag.key);
    const price = this.keepWaiting(line, snap(yToPrice(y, g), this.step()));
    this.drag.set({ ...drag, price, moved: drag.moved || Math.abs(y - drag.startY) > 3 });
    if (line && !line.order) {
      this.changeDraft(line, price);
    }
  }

  /** A tap or click on the chart itself: on touch screens, shows the price there and its +; closes an open menu. */
  protected pointerDown(event: PointerEvent): void {
    if ((event.target as HTMLElement).closest('[data-chart-control]')) {
      return;
    }
    this.menu.set(null);
    if (event.pointerType !== 'mouse') {
      this.hoverY.set(this.frameY(event));
    }
  }

  protected pointerUp(): void {
    const drag = this.drag();
    if (drag && !drag.keyboard) {
      this.finishDrag(drag);
    }
  }

  /** Stops a drag without moving anything: a working order goes back to its price (a draft keeps its new one). */
  protected cancelDrag(): void {
    this.drag.set(null);
  }

  protected keyDown(event: KeyboardEvent, line: Line): void {
    const step = this.step() * (event.shiftKey ? 10 : 1);
    switch (event.key) {
      case 'ArrowUp':
      case 'ArrowDown': {
        event.preventDefault();
        const drag = this.drag();
        const from = drag?.key === line.key ? drag.price : line.price;
        const price = this.keepWaiting(line, snap(from + (event.key === 'ArrowUp' ? step : -step), this.step()));
        if (!line.order) {
          this.changeDraft(line, price);
          return;
        }
        if (!drag) {
          this.frozenExtras = this.extras();
        }
        this.drag.set({ key: line.key, startY: 0, price, moved: true, keyboard: true });
        return;
      }
      case 'Enter': {
        const drag = this.drag();
        if (line.order && drag?.key === line.key) {
          event.preventDefault();
          this.finishDrag(drag);
        }
        return;
      }
      case 'Escape':
        this.drag.set(null);
        return;
      case 'Delete':
      case 'Backspace':
        event.preventDefault();
        this.remove(line);
        return;
    }
  }

  /** Leaving a line's label with a keyboard move not yet placed with Enter puts it back. */
  protected keyboardBlur(line: Line): void {
    if (this.drag()?.key === line.key && this.drag()?.keyboard) {
      this.drag.set(null);
    }
  }

  private finishDrag(drag: Drag): void {
    this.drag.set(null);
    const line = this.lines().find(l => l.key === drag.key);
    if (!line?.order || !drag.moved || drag.price === line.order.price) {
      return;
    }
    this.dropped.update(map => new Map(map).set(line.order!.orderId, drag.price));
    this.moveOrder.emit({ order: line.order, price: drag.price });
  }

  /**
   * The draft follows the pointer across the market price, turning from a limit into a stop order or back, as on
   * TradingView: a buy below the market is a limit, above it a stop.
   */
  private changeDraft(line: Line, price: number): void {
    const market = this.livePrice();
    const kind = market == null || waitsAt(line.side, line.kind, price, market) ? line.kind
      : line.kind === 'LIMIT' ? 'STOP' : 'LIMIT';
    this.draftChange.emit({ price, kind });
  }

  /**
   * A working order can't be dragged past the market price: there it would fill at once, which the platform turns
   * down, so it stops a step short.
   */
  private keepWaiting(line: Line | undefined, price: number): number {
    const market = this.livePrice();
    if (!line?.order || market == null || waitsAt(line.side, line.kind, price, market)) {
      return Math.max(price, this.step());
    }
    const waitsForFall = (line.kind === 'LIMIT') === (line.side === 'BUY');
    return snap(waitsForFall ? market - this.step() : market + this.step(), this.step());
  }

  private frameY(event: PointerEvent): number {
    return event.clientY - this.frame().nativeElement.getBoundingClientRect().top;
  }
}
