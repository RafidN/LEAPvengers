TRUNCATE TABLE
	price_quotes,
	cash_transactions,
	orders,
	holdings,
	users,
	accounts,
	clients,
	instruments
RESTART IDENTITY CASCADE;

-- Clients
INSERT INTO clients (first_name, last_name, email) VALUES
('Test', 'User', 'test@test.com'),
('Paula', 'Agyeman', 'paula.agyeman@lol.com'),
('Rafid', 'Nasery', 'rafid.nasery@lol.com'),
('Sam', 'Onukweme', 'sam.onukweme@lol.com'),
('Bryan', 'Nguyen', 'bryan.nguyen@lol.com'),
('Mark', 'Bounheuangvilay', 'mark.bounheuangvilay@lol.com')
ON CONFLICT (email) DO NOTHING;

-- Instruments for Yahoo Finance historical backfill.
-- market reflects where the ticker is quoted/listed: US-listed names (including ADRs for SAP, ASML
-- and NVO), Shell's UK primary listing, and NSE-listed (.NS) Indian equities. Crypto and FX pairs
-- are USD-quoted and global, so they're recorded as US market (no better fit in the US/UK/IN set).
INSERT INTO instruments (ticker, instrument_name, asset_class, market) VALUES
('NFLX', 'Netflix, Inc.', 'Equity', 'US'),
('AMD', 'Advanced Micro Devices, Inc.', 'Equity', 'US'),
('ORCL', 'Oracle Corporation', 'Equity', 'US'),
('SAP', 'SAP SE', 'Equity', 'US'),
('ASML', 'ASML Holding N.V.', 'Equity', 'US'),
('SHEL', 'Shell plc', 'Equity', 'UK'),
('NVO', 'Novo Nordisk A/S', 'Equity', 'US'),
('RELIANCE.NS', 'Reliance Industries Limited', 'Equity', 'IN'),
('TCS.NS', 'Tata Consultancy Services Limited', 'Equity', 'IN'),
('HDFCBANK.NS', 'HDFC Bank Limited', 'Equity', 'IN'),
('BTC-USD', 'Bitcoin / US Dollar', 'Crypto', 'US'),
('ETH-USD', 'Ethereum / US Dollar', 'Crypto', 'US'),
('SOL-USD', 'Solana / US Dollar', 'Crypto', 'US'),
('EURUSD=X', 'Euro / US Dollar', 'FX', 'US'),
('GBPUSD=X', 'British Pound / US Dollar', 'FX', 'US'),
('USDJPY=X', 'US Dollar / Japanese Yen', 'FX', 'US')
ON CONFLICT (ticker) DO NOTHING;

-- Accounts
INSERT INTO accounts (client_id, opened_date, balance) VALUES
((SELECT client_id FROM clients WHERE email = 'test@test.com'), '2026-01-01', 100000.00),
((SELECT client_id FROM clients WHERE email = 'paula.agyeman@lol.com'), '2026-01-15', 150000.00),
((SELECT client_id FROM clients WHERE email = 'rafid.nasery@lol.com'), '2026-01-20', 125000.00),
((SELECT client_id FROM clients WHERE email = 'sam.onukweme@lol.com'), '2026-01-25', 200000.00),
((SELECT client_id FROM clients WHERE email = 'bryan.nguyen@lol.com'), '2026-02-01', 175000.00),
((SELECT client_id FROM clients WHERE email = 'mark.bounheuangvilay@lol.com'), '2026-02-05', 180000.00)
ON CONFLICT DO NOTHING;

-- Users (authentication)
-- Password hash for: Test123
-- Traders belong to a client. Ops and Analyst are internal staff, so their client_id is NULL.
INSERT INTO users (client_id, username, password, role, is_active) VALUES
((SELECT client_id FROM clients WHERE email = 'test@test.com'), 'test', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
((SELECT client_id FROM clients WHERE email = 'paula.agyeman@lol.com'), 'paula', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
((SELECT client_id FROM clients WHERE email = 'rafid.nasery@lol.com'), 'rafid', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
((SELECT client_id FROM clients WHERE email = 'sam.onukweme@lol.com'), 'sam', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
((SELECT client_id FROM clients WHERE email = 'bryan.nguyen@lol.com'), 'bryan', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
((SELECT client_id FROM clients WHERE email = 'mark.bounheuangvilay@lol.com'), 'mark', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
(NULL, 'ops', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'OPS', true),
(NULL, 'analyst', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'ANALYST', true)
ON CONFLICT (username) DO NOTHING;

-- Cash Transactions (Deposits)
INSERT INTO cash_transactions (account_id, txn_type, amount, txn_date) VALUES
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'test@test.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 100000.00, '2026-01-01'),
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'paula.agyeman@lol.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 150000.00, '2026-01-15'),
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'rafid.nasery@lol.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 125000.00, '2026-01-20'),
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'sam.onukweme@lol.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 200000.00, '2026-01-25'),
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'bryan.nguyen@lol.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 175000.00, '2026-02-01'),
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'mark.bounheuangvilay@lol.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 180000.00, '2026-02-05')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- Demo personas from the BRS (section 6). All passwords are Test123.
--   joanna - Joanna Mitchell, self-directed investor (TRADER) with a year of activity
--   david  - head of trading operations (OPS), internal so no client_id
--   priya  - commercial analyst (ANALYST), internal so no client_id
-- Dates are relative to the day the seed runs, so the "today" and "7 days" history filters
-- always have something to show.
-- ============================================================================
INSERT INTO clients (first_name, last_name, email) VALUES
('Joanna', 'Mitchell', 'joanna.mitchell@example.com')
ON CONFLICT (email) DO NOTHING;

INSERT INTO accounts (client_id, opened_date, balance) VALUES
((SELECT client_id FROM clients WHERE email = 'joanna.mitchell@example.com'), CURRENT_DATE - 120, 0);

INSERT INTO users (client_id, username, password, role, is_active) VALUES
((SELECT client_id FROM clients WHERE email = 'joanna.mitchell@example.com'), 'joanna', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'TRADER', true),
(NULL, 'david', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'OPS', true),
(NULL, 'priya', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', 'ANALYST', true)
ON CONFLICT (username) DO NOTHING;

-- Joanna funds her account in two deposits and takes a little out. Sam tops up so he can hold
-- enough Bitcoin to land in the Premier segment.
INSERT INTO cash_transactions (account_id, txn_type, amount, txn_date)
SELECT a.account_id, v.txn_type, v.amount, CURRENT_DATE - v.days_ago
FROM (VALUES
    ('joanna.mitchell@example.com', 'DEPOSIT',    50000.00, 118),
    ('joanna.mitchell@example.com', 'DEPOSIT',    10000.00,  40),
    ('joanna.mitchell@example.com', 'WITHDRAWAL',  2500.00,   6),
    ('sam.onukweme@lol.com',        'DEPOSIT',   200000.00,  80)
) AS v(email, txn_type, amount, days_ago)
JOIN clients c ON c.email = v.email
JOIN accounts a ON a.client_id = c.client_id;

-- Orders. Filled orders move holdings through the orders_sync_holdings trigger.
-- Joanna's history covers every instrument class and every current status, including a sell that
-- was rejected because she tried to sell more SHEL than she holds.
INSERT INTO orders (
    account_id,
    instrument_id,
    order_type,
    quantity,
    price,
    order_date,
    order_status,
    rejection_reason,
    execution_price,
    submitted_at,
    executed_at
)
SELECT a.account_id, i.instrument_id, v.side, v.quantity, v.price,
       (now() - v.ago)::date, v.status, v.rejection_reason,
       CASE WHEN v.status = 'FILLED' THEN v.price END,
       now() - v.ago,
       CASE WHEN v.status = 'FILLED' THEN now() - v.ago + interval '2 seconds' END
FROM (VALUES
    ('joanna.mitchell@example.com', 'NFLX',        'BUY',    40,      76.50, 'FILLED',   NULL,                                      interval '85 days 3 hours'),
    ('joanna.mitchell@example.com', 'AMD',         'BUY',    10,     590.00, 'FILLED',   NULL,                                      interval '60 days 5 hours'),
    ('joanna.mitchell@example.com', 'BTC-USD',     'BUY',     0.05, 91000.00, 'FILLED',   NULL,                                      interval '45 days 2 hours'),
    ('joanna.mitchell@example.com', 'SHEL',        'BUY',    20,      96.10, 'FILLED',   NULL,                                      interval '30 days 4 hours'),
    ('joanna.mitchell@example.com', 'RELIANCE.NS', 'BUY',     5,    1520.00, 'FILLED',   NULL,                                      interval '20 days 6 hours'),
    ('joanna.mitchell@example.com', 'NFLX',        'SELL',   10,      79.20, 'FILLED',   NULL,                                      interval '10 days 1 hour'),
    ('joanna.mitchell@example.com', 'SAP',         'BUY',     5,     224.00, 'FAILED',   'Order canceled before execution',         interval '7 days 2 hours'),
    ('joanna.mitchell@example.com', 'ETH-USD',     'BUY',     1,    3120.00, 'FILLED',   NULL,                                      interval '5 days 3 hours'),
    ('joanna.mitchell@example.com', 'EURUSD=X',    'BUY',  1000,       1.1310, 'FILLED', NULL,                                      interval '3 days 2 hours'),
    ('joanna.mitchell@example.com', 'ASML',        'BUY',    20,    1850.00, 'REJECTED', 'Insufficient buying power for the order', interval '2 days 1 hour'),
    ('joanna.mitchell@example.com', 'SHEL',        'SELL',   30,      98.00, 'REJECTED', 'Insufficient holdings to complete sale',  interval '1 day 2 hours'),
    ('joanna.mitchell@example.com', 'ORCL',        'BUY',     2,     158.00, 'PENDING',  NULL,                                      interval '20 minutes'),
    -- Sam: one large Bitcoin position -> Premier once he also has 12 recent fills (below)
    ('sam.onukweme@lol.com',        'BTC-USD',     'BUY',     3,   90000.00, 'FILLED',   NULL,                                      interval '70 days'),
    -- Bryan: a couple of trades -> Core
    ('bryan.nguyen@lol.com',        'ASML',        'BUY',    15,    1800.00, 'FILLED',   NULL,                                      interval '50 days'),
    ('bryan.nguyen@lol.com',        'TCS.NS',      'BUY',    10,    2250.00, 'FILLED',   NULL,                                      interval '15 days')
) AS v(email, ticker, side, quantity, price, status, rejection_reason, ago)
JOIN clients c ON c.email = v.email
JOIN accounts a ON a.client_id = c.client_id
JOIN instruments i ON i.ticker = v.ticker
ORDER BY v.ago DESC;

-- Frequent small traders: Paula (12 fills -> Active) and Sam (11 more fills -> Premier).
INSERT INTO orders (
    account_id,
    instrument_id,
    order_type,
    quantity,
    price,
    order_date,
    order_status,
    rejection_reason,
    execution_price,
    submitted_at,
    executed_at
)
SELECT a.account_id, i.instrument_id, 'BUY', v.quantity, v.base_price + g, (now() - g * interval '6 days')::date,
       'FILLED', NULL, v.base_price + g, now() - g * interval '6 days' - interval '1 hour',
       now() - g * interval '6 days' - interval '1 hour' + interval '2 seconds'
FROM (VALUES
    ('paula.agyeman@lol.com', 'SOL-USD', 5,  145.00, 12),
    ('sam.onukweme@lol.com',  'NVO',     20,  42.00, 11)
) AS v(email, ticker, quantity, base_price, fills)
JOIN clients c ON c.email = v.email
JOIN accounts a ON a.client_id = c.client_id
JOIN instruments i ON i.ticker = v.ticker
CROSS JOIN LATERAL generate_series(v.fills, 1, -1) AS g;

-- Cash balance = deposits - withdrawals - filled buys + filled sells, so balances always agree
-- with the history the seed just wrote.
UPDATE accounts a
SET balance =
    COALESCE((SELECT SUM(CASE WHEN ct.txn_type = 'DEPOSIT' THEN ct.amount ELSE -ct.amount END)
              FROM cash_transactions ct WHERE ct.account_id = a.account_id), 0)
  - COALESCE((SELECT SUM(CASE WHEN o.order_type = 'BUY' THEN o.quantity * o.price ELSE -o.quantity * o.price END)
              FROM orders o WHERE o.account_id = a.account_id AND o.order_status = 'FILLED'), 0);

-- Price quotes are backfilled from Yahoo Finance during setup, which also refreshes the
-- latest_price_quotes and account_valuations views that value these holdings.