/**
 * Pure helpers for drawing and dragging order lines over a price chart: price <-> pixel on the chart's y axis,
 * how finely a dragged price snaps, and which orders a price above or below the market can be.
 */
export type OrderSide = 'BUY' | 'SELL';
export type OrderKind = 'LIMIT' | 'STOP';

/** Where Chart.js drew the plot, and the prices at its top and bottom edges. */
export interface ChartGeometry {
  left: number;
  right: number;
  top: number;
  bottom: number;
  /** The price at the bottom edge. */
  min: number;
  /** The price at the top edge. */
  max: number;
}

/** Pixels from the top of the chart for a price; Chart.js's linear scale maps the same way. */
export function priceToY(price: number, g: ChartGeometry): number {
  return g.top + (g.max - price) / (g.max - g.min) * (g.bottom - g.top);
}

/** The price at a pixel, kept inside the plot. */
export function yToPrice(y: number, g: ChartGeometry): number {
  const clamped = Math.min(Math.max(y, g.top), g.bottom);
  return g.max - (clamped - g.top) / (g.bottom - g.top) * (g.max - g.min);
}

/**
 * How finely a dragged price snaps: about a pixel's worth of the visible range, as a round number. Never finer than a
 * cent for prices of $10 and up, nor than the 4 decimal places prices are stored to below that. Bitcoin snaps to $10,
 * a $150 stock to a cent, EUR/USD to 0.0001.
 */
export function priceStep(g: ChartGeometry): number {
  const perPixel = (g.max - g.min) / Math.max(g.bottom - g.top, 1);
  const step = 10 ** Math.floor(Math.log10(Math.max(perPixel, 1e-9)));
  return Math.max(step, g.min >= 10 ? 0.01 : 0.0001);
}

/** price rounded to the nearest step, without floating-point dust (e.g. 1.1213 rather than 1.12130000001). */
export function snap(price: number, step: number): number {
  const decimals = decimalsOf(step);
  return Number((Math.round(price / step) * step).toFixed(decimals));
}

/** Decimal places a step has: 2 for 0.01, 0 for 10. */
export function decimalsOf(step: number): number {
  return step >= 1 ? 0 : Math.round(-Math.log10(step));
}

/**
 * The orders a price can start, as TradingView offers them: below the market, a limit buy or a stop-loss sell;
 * above it, a limit sell or a stop buy. A limit order waits for a better price, a stop order for a worse one.
 */
export function ordersAt(price: number, marketPrice: number): { side: OrderSide; kind: OrderKind }[] {
  return price < marketPrice
    ? [{ side: 'BUY', kind: 'LIMIT' }, { side: 'SELL', kind: 'STOP' }]
    : [{ side: 'SELL', kind: 'LIMIT' }, { side: 'BUY', kind: 'STOP' }];
}

/** Whether a limit or stop price is on the side of the market where it waits rather than fills at once. */
export function waitsAt(side: OrderSide, kind: OrderKind, price: number, marketPrice: number): boolean {
  const waitsForFall = (kind === 'LIMIT') === (side === 'BUY');
  return waitsForFall ? price < marketPrice : price > marketPrice;
}

/** "Buy limit", "Sell stop" and so on; a sell stop is a stop-loss. */
export function orderName(side: OrderSide, kind: OrderKind): string {
  if (side === 'SELL' && kind === 'STOP') {
    return 'Stop-loss';
  }
  return `${side === 'BUY' ? 'Buy' : 'Sell'} ${kind === 'LIMIT' ? 'limit' : 'stop'}`;
}
