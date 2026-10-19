import { ChartGeometry, orderName, ordersAt, priceStep, priceToY, snap, waitsAt, yToPrice } from './chart-geometry';

describe('chart geometry', () => {
  // A 200 px tall plot showing $100 at the bottom to $200 at the top
  const g: ChartGeometry = { left: 0, right: 500, top: 20, bottom: 220, min: 100, max: 200 };

  it('maps prices to pixels and back', () => {
    expect(priceToY(200, g)).toBe(20);
    expect(priceToY(100, g)).toBe(220);
    expect(priceToY(150, g)).toBe(120);
    expect(yToPrice(120, g)).toBe(150);
  });

  it('keeps a dragged price inside the plot', () => {
    expect(yToPrice(-50, g)).toBe(200);
    expect(yToPrice(400, g)).toBe(100);
  });

  it('snaps about a pixel of the visible range to a round step', () => {
    // $100 over 200 px is $0.50 a pixel
    expect(priceStep(g)).toBe(0.1);
    expect(priceStep({ ...g, min: 80_000, max: 82_000 })).toBe(10);
    expect(priceStep({ ...g, min: 1.12, max: 1.125 })).toBe(0.0001);
  });

  it('rounds without floating-point dust', () => {
    expect(snap(1.12134, 0.0001)).toBe(1.1213);
    expect(snap(81_756.53, 10)).toBe(81_760);
    expect(snap(0.1 + 0.2, 0.1)).toBe(0.3);
  });

  it('offers a limit buy or stop-loss below the market and a limit sell or stop buy above it', () => {
    expect(ordersAt(95, 100)).toEqual([{ side: 'BUY', kind: 'LIMIT' }, { side: 'SELL', kind: 'STOP' }]);
    expect(ordersAt(105, 100)).toEqual([{ side: 'SELL', kind: 'LIMIT' }, { side: 'BUY', kind: 'STOP' }]);
  });

  it('knows which side of the market each order waits on', () => {
    expect(waitsAt('BUY', 'LIMIT', 95, 100)).toBe(true);
    expect(waitsAt('BUY', 'LIMIT', 100, 100)).toBe(false);
    expect(waitsAt('SELL', 'STOP', 95, 100)).toBe(true);
    expect(waitsAt('SELL', 'LIMIT', 95, 100)).toBe(false);
    expect(waitsAt('BUY', 'STOP', 105, 100)).toBe(true);
  });

  it('calls a sell stop a stop-loss', () => {
    expect(orderName('SELL', 'STOP')).toBe('Stop-loss');
    expect(orderName('BUY', 'LIMIT')).toBe('Buy limit');
  });
});
