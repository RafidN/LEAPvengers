import { Page, Route } from '@playwright/test';

type Role = 'TRADER' | 'OPS' | 'ANALYST';

type LoginUser = {
  username: string;
  role: Role;
  firstName: string;
  lastName: string;
  userId: number;
  clientId?: number;
  email: string;
};

type Instrument = {
  instrumentId: number;
  ticker: string;
  instrumentName: string;
  assetClass: 'Equity' | 'Crypto' | 'FX';
  market: 'US' | 'UK' | 'IN';
};

type Quote = {
  ticker: string;
  instrumentName: string;
  currentPrice: number;
  assetClass: string;
};

const NOW = '2026-10-21T12:00:00.000Z';

const LOGIN_USERS: Record<string, LoginUser> = {
  joanna: {
    username: 'joanna',
    role: 'TRADER',
    firstName: 'Joanna',
    lastName: 'Mills',
    userId: 101,
    clientId: 1,
    email: 'joanna@example.com'
  },
  david: {
    username: 'david',
    role: 'OPS',
    firstName: 'David',
    lastName: 'Stone',
    userId: 201,
    email: 'david@example.com'
  },
  priya: {
    username: 'priya',
    role: 'ANALYST',
    firstName: 'Priya',
    lastName: 'Rao',
    userId: 301,
    email: 'priya@example.com'
  }
};

const INSTRUMENTS: Instrument[] = [
  { instrumentId: 1, ticker: 'NFLX', instrumentName: 'Netflix Inc.', assetClass: 'Equity', market: 'US' },
  { instrumentId: 2, ticker: 'AMD', instrumentName: 'Advanced Micro Devices', assetClass: 'Equity', market: 'US' },
  { instrumentId: 3, ticker: 'SHEL.L', instrumentName: 'Shell PLC', assetClass: 'Equity', market: 'UK' },
  { instrumentId: 4, ticker: 'RELIANCE.NS', instrumentName: 'Reliance Industries', assetClass: 'Equity', market: 'IN' },
  { instrumentId: 5, ticker: 'BTC-USD', instrumentName: 'Bitcoin', assetClass: 'Crypto', market: 'US' },
  { instrumentId: 6, ticker: 'EURUSD=X', instrumentName: 'Euro / US Dollar', assetClass: 'FX', market: 'US' }
];

const QUOTES: Quote[] = [
  { ticker: 'NFLX', instrumentName: 'Netflix Inc.', currentPrice: 629.55, assetClass: 'Equity' },
  { ticker: 'AMD', instrumentName: 'Advanced Micro Devices', currentPrice: 154.1, assetClass: 'Equity' },
  { ticker: 'SHEL.L', instrumentName: 'Shell PLC', currentPrice: 28.04, assetClass: 'Equity' },
  { ticker: 'RELIANCE.NS', instrumentName: 'Reliance Industries', currentPrice: 36.61, assetClass: 'Equity' },
  { ticker: 'BTC-USD', instrumentName: 'Bitcoin', currentPrice: 70200.01, assetClass: 'Crypto' },
  { ticker: 'EURUSD=X', instrumentName: 'Euro / US Dollar', currentPrice: 1.0942, assetClass: 'FX' }
];

function base64Url(value: string): string {
  return Buffer.from(value).toString('base64url');
}

function fakeJwt(username: string, role: Role): string {
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    sub: username,
    role,
    exp: Math.floor(Date.now() / 1000) + 60 * 60
  }));
  return `${header}.${payload}.playwright`;
}

function historyPointsFor(ticker: string) {
  const baseByTicker: Record<string, number> = {
    NFLX: 619,
    AMD: 150,
    'SHEL.L': 27.8,
    'RELIANCE.NS': 35.9,
    'BTC-USD': 69000,
    'EURUSD=X': 1.09
  };
  const base = baseByTicker[ticker] ?? 100;
  return [
    { ticker, instrumentName: ticker, price: base, volume: 1000, quoteTimestamp: '2026-10-20T09:30:00.000Z' },
    { ticker, instrumentName: ticker, price: base * 1.01, volume: 1020, quoteTimestamp: '2026-10-20T12:00:00.000Z' },
    { ticker, instrumentName: ticker, price: base * 1.015, volume: 1050, quoteTimestamp: NOW }
  ];
}

function orderRecord() {
  return {
    orderId: 5001,
    accountId: 1,
    ticker: 'NFLX',
    instrumentName: 'Netflix Inc.',
    orderType: 'BUY',
    quantity: 2,
    price: 625,
    orderStatus: 'FILLED',
    orderDate: NOW,
    submittedAt: NOW,
    executedAt: NOW,
    executionPrice: 625,
    orderKind: 'MARKET'
  };
}

async function fulfillJson(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body)
  });
}

function queryText(requestBody: unknown): string {
  const value = (requestBody as { query?: string } | undefined)?.query;
  return typeof value === 'string' ? value.trim().toLowerCase() : '';
}

export async function mockApi(page: Page) {
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;

    if (path === '/api/auth/login' && request.method() === 'POST') {
      const post = request.postDataJSON() as { username?: string };
      const user = post?.username ? LOGIN_USERS[post.username] : undefined;
      if (!user) {
        await fulfillJson(route, 401, { message: 'Invalid credentials' });
        return;
      }
      await fulfillJson(route, 200, {
        token: fakeJwt(user.username, user.role),
        userId: user.userId,
        firstName: user.firstName,
        lastName: user.lastName,
        username: user.username,
        clientId: user.clientId,
        email: user.email,
        role: user.role
      });
      return;
    }

    if (path === '/api/accounts') {
      await fulfillJson(route, 200, [{ accountId: 1, openedDate: '2025-01-01', balance: 10000 }]);
      return;
    }

    if (path === '/api/accounts/1/portfolio') {
      await fulfillJson(route, 200, {
        accountId: 1,
        balance: 10000,
        holdings: [
          {
            ticker: 'NFLX',
            instrumentName: 'Netflix Inc.',
            assetClass: 'Equity',
            quantity: 4,
            latestPrice: 629.55,
            value: 2518.2,
            priceTimestamp: NOW
          }
        ],
        totalBalance: 12518.2,
        cashHeldForOrders: 0
      });
      return;
    }

    if (path === '/api/accounts/value-history') {
      await fulfillJson(route, 200, [
        { time: '2026-10-18T12:00:00.000Z', totalValue: 12000, cash: 9800, holdingsValue: 2200 },
        { time: '2026-10-19T12:00:00.000Z', totalValue: 12250, cash: 9800, holdingsValue: 2450 },
        { time: NOW, totalValue: 12518.2, cash: 10000, holdingsValue: 2518.2 }
      ]);
      return;
    }

    if (path === '/api/history/orders') {
      await fulfillJson(route, 200, [orderRecord()]);
      return;
    }

    if (path === '/api/history/cash') {
      await fulfillJson(route, 200, [
        {
          cashTransactionId: 1,
          transactionType: 'DEPOSIT',
          amount: 10000,
          transactionDate: NOW,
          transactionTime: NOW,
          runningBalance: 10000
        }
      ]);
      return;
    }

    if (path === '/api/history/prices') {
      const ticker = url.searchParams.get('ticker') ?? 'NFLX';
      await fulfillJson(route, 200, historyPointsFor(ticker));
      return;
    }

    if (path === '/api/search/instrument-price-quote' && request.method() === 'POST') {
      const post = request.postDataJSON() as unknown;
      const q = queryText(post);
      const filtered = q
        ? QUOTES.filter(quote => {
            const haystack = `${quote.ticker} ${quote.instrumentName}`.toLowerCase();
            return haystack.includes(q);
          })
        : QUOTES;
      await fulfillJson(route, 200, filtered.map(quote => ({ ...quote, quoteTimestamp: NOW })));
      return;
    }

    if (path === '/api/market/news') {
      await fulfillJson(route, 200, [
        {
          title: 'Netflix announces new subscriber milestone',
          link: 'https://example.com/news/netflix',
          source: 'Example News',
          publishedAt: NOW,
          summary: 'NFLX continues to trend in streaming markets.'
        }
      ]);
      return;
    }

    if (path === '/api/market/movers') {
      await fulfillJson(route, 200, {
        gainers: [{ ticker: 'NFLX', name: 'Netflix Inc.', price: 629.55, change: 10.1, changePercent: 1.63 }],
        losers: [{ ticker: 'AMD', name: 'Advanced Micro Devices', price: 154.1, change: -1.4, changePercent: -0.9 }],
        asOf: NOW,
        source: 'Example Feed'
      });
      return;
    }

    if (path === '/api/instruments') {
      await fulfillJson(route, 200, INSTRUMENTS);
      return;
    }

    if (path.startsWith('/api/instruments/quotes/')) {
      const instrumentId = Number(path.split('/').pop());
      const instrument = INSTRUMENTS.find(item => item.instrumentId === instrumentId);
      const quote = QUOTES.find(item => item.ticker === instrument?.ticker);
      if (!instrument || !quote) {
        await fulfillJson(route, 404, { message: 'Not found' });
        return;
      }
      await fulfillJson(route, 200, {
        price: quote.currentPrice,
        timestamp: NOW,
        source: 'SIMULATED',
        delayMinutes: 0,
        tradable: true,
        marketOpen: true,
        market: instrument.market,
        notTradableReason: null
      });
      return;
    }

    if (path === '/api/orders' && request.method() === 'GET') {
      await fulfillJson(route, 200, [orderRecord()]);
      return;
    }

    if (path === '/api/orders/working' && request.method() === 'GET') {
      await fulfillJson(route, 200, []);
      return;
    }

    if (path === '/api/clients/segments') {
      await fulfillJson(route, 200, [
        {
          clientId: 1,
          firstName: 'Olivia',
          lastName: 'Carter',
          email: 'olivia@example.com',
          totalPortfolioValue: 12518.2,
          recentFilledOrderCount: 12,
          segment: 'Active'
        },
        {
          clientId: 2,
          firstName: 'Liam',
          lastName: 'Morris',
          email: 'liam@example.com',
          totalPortfolioValue: 245000,
          recentFilledOrderCount: 48,
          segment: 'Premier'
        }
      ]);
      return;
    }

    if (path === '/api/internal/audit/disputes' && request.method() === 'GET') {
      await fulfillJson(route, 200, [
        {
          disputeId: 77,
          orderId: 5001,
          clientId: 1,
          clientName: 'Olivia Carter',
          ticker: 'NFLX',
          orderType: 'BUY',
          quantity: 2,
          orderStatus: 'FILLED',
          reason: 'WRONG_PRICE',
          message: 'The fill price looks wrong.',
          status: 'OPEN',
          createdAt: NOW,
          raisedByUsername: 'olivia'
        }
      ]);
      return;
    }

    if (path === '/api/internal/audit/orders' && request.method() === 'GET') {
      await fulfillJson(route, 200, {
        content: [
          {
            ...orderRecord(),
            clientId: 1,
            clientName: 'Olivia Carter'
          }
        ],
        number: 0,
        totalElements: 1,
        totalPages: 1
      });
      return;
    }

    if (path === '/api/internal/audit/orders/5001' && request.method() === 'GET') {
      await fulfillJson(route, 200, {
        order: { ...orderRecord(), clientId: 1, clientName: 'Olivia Carter' },
        client: {
          clientId: 1,
          firstName: 'Olivia',
          lastName: 'Carter',
          email: 'olivia@example.com',
          segment: 'Active'
        },
        account: {
          accountId: 1,
          openedDate: '2025-01-01',
          balance: 10000
        },
        orderEvents: [
          {
            eventId: 1,
            orderId: 5001,
            fromStatus: null,
            toStatus: 'SUBMITTED',
            createdAt: NOW,
            details: 'Order submitted'
          },
          {
            eventId: 2,
            orderId: 5001,
            fromStatus: 'SUBMITTED',
            toStatus: 'FILLED',
            createdAt: NOW,
            details: 'Order filled'
          }
        ],
        settlement: {
          cashTransactionId: 10,
          transactionType: 'BUY',
          amount: 1250,
          transactionDate: NOW,
          transactionTime: NOW
        },
        disputes: [
          {
            disputeId: 77,
            orderId: 5001,
            ticker: 'NFLX',
            orderType: 'BUY',
            quantity: 2,
            orderStatus: 'FILLED',
            reason: 'WRONG_PRICE',
            message: 'The fill price looks wrong.',
            status: 'OPEN',
            createdAt: NOW,
            raisedByUsername: 'olivia'
          }
        ]
      });
      return;
    }

    if (path === '/api/internal/reports/activity' && request.method() === 'GET') {
      await fulfillJson(route, 200, {
        from: '2026-10-01',
        to: '2026-10-21',
        groupBy: 'day',
        dataAsOf: NOW,
        rows: [
          {
            key: '2026-10-20',
            label: 'Oct 20, 2026',
            trades: 4,
            buys: 2,
            sells: 2,
            tradedValue: 20000,
            activeClients: 3
          },
          {
            key: '2026-10-21',
            label: 'Oct 21, 2026',
            trades: 6,
            buys: 4,
            sells: 2,
            tradedValue: 30000,
            activeClients: 4
          }
        ],
        totals: {
          key: 'total',
          label: 'Total',
          trades: 10,
          buys: 6,
          sells: 4,
          tradedValue: 50000,
          activeClients: 5
        }
      });
      return;
    }

    if (path === '/api/internal/reports/order-health' && request.method() === 'GET') {
      await fulfillJson(route, 200, {
        from: '2026-10-01',
        to: '2026-10-21',
        totalOrders: 10,
        submittedOrders: 10,
        acceptedOrders: 9,
        filledOrders: 8,
        rejectedOrders: 1,
        failedOrders: 1,
        successRate: 0.8,
        failureRate: 0.2,
        statusCounts: [
          { status: 'FILLED', count: 8 },
          { status: 'REJECTED', count: 1 },
          { status: 'FAILED', count: 1 }
        ],
        dailyRates: [
          {
            date: '2026-10-20',
            totalOrders: 4,
            filledOrders: 3,
            rejectedOrders: 1,
            failedOrders: 0,
            successRate: 0.75,
            failureRate: 0.25
          },
          {
            date: '2026-10-21',
            totalOrders: 6,
            filledOrders: 5,
            rejectedOrders: 0,
            failedOrders: 1,
            successRate: 0.8333,
            failureRate: 0.1667
          }
        ]
      });
      return;
    }

    if (path === '/api/analytics/client-insights') {
      await fulfillJson(route, 200, {
        clients: [
          { clientId: 1, segment: 'Active', totalPortfolioValue: 12518.2, recentFilledOrderCount: 12 },
          { clientId: 2, segment: 'Premier', totalPortfolioValue: 245000, recentFilledOrderCount: 48 }
        ]
      });
      return;
    }

    if (path === '/api/insights/instruments' && request.method() === 'POST') {
      await fulfillJson(route, 200, [
        {
          ticker: 'NFLX',
          instrumentName: 'Netflix Inc.',
          assetClass: 'Equity',
          currentPositionValue: 180000,
          recentFilledOrderCount: 15,
          buyCount: 9,
          sellCount: 6,
          tradedClientCount: 5
        },
        {
          ticker: 'AMD',
          instrumentName: 'Advanced Micro Devices',
          assetClass: 'Equity',
          currentPositionValue: 94000,
          recentFilledOrderCount: 11,
          buyCount: 7,
          sellCount: 4,
          tradedClientCount: 4
        }
      ]);
      return;
    }

    await fulfillJson(route, 200, {});
  });
}

export async function seedAuthSession(page: Page, user: LoginUser) {
  await page.addInitScript(session => {
    localStorage.setItem('authToken', session.token);
    localStorage.setItem('userId', String(session.userId));
    localStorage.setItem('firstName', session.firstName);
    localStorage.setItem('lastName', session.lastName);
    localStorage.setItem('username', session.username);
    localStorage.setItem('role', session.role);
    localStorage.setItem('email', session.email);
    if (session.clientId) {
      localStorage.setItem('clientId', String(session.clientId));
    }
  }, {
    token: fakeJwt(user.username, user.role),
    userId: user.userId,
    firstName: user.firstName,
    lastName: user.lastName,
    username: user.username,
    role: user.role,
    email: user.email,
    clientId: user.clientId
  });
}

export const USERS = LOGIN_USERS;
