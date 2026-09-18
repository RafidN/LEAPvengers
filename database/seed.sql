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
('Test', 'User', 'test@test.com')
ON CONFLICT (email) DO NOTHING;

-- Instruments for Yahoo Finance historical backfill.
INSERT INTO instruments (ticker, instrument_name, asset_class) VALUES
('NFLX', 'Netflix, Inc.', 'Equity'),
('AMD', 'Advanced Micro Devices, Inc.', 'Equity'),
('ORCL', 'Oracle Corporation', 'Equity'),
('SAP', 'SAP SE', 'Equity'),
('ASML', 'ASML Holding N.V.', 'Equity'),
('SHEL', 'Shell plc', 'Equity'),
('NVO', 'Novo Nordisk A/S', 'Equity'),
('RELIANCE.NS', 'Reliance Industries Limited', 'Equity'),
('TCS.NS', 'Tata Consultancy Services Limited', 'Equity'),
('HDFCBANK.NS', 'HDFC Bank Limited', 'Equity'),
('BTC-USD', 'Bitcoin / US Dollar', 'Cash'),
('ETH-USD', 'Ethereum / US Dollar', 'Cash'),
('SOL-USD', 'Solana / US Dollar', 'Cash'),
('EURUSD=X', 'Euro / US Dollar', 'Cash'),
('GBPUSD=X', 'British Pound / US Dollar', 'Cash'),
('USDJPY=X', 'US Dollar / Japanese Yen', 'Cash')
ON CONFLICT (ticker) DO NOTHING;

-- Accounts
INSERT INTO accounts (client_id, opened_date, balance) VALUES
((SELECT client_id FROM clients WHERE email = 'test@test.com'), '2026-01-01', 100000.00)
ON CONFLICT DO NOTHING;

-- Users (authentication)
-- Password hash for: Test123
INSERT INTO users (client_id, username, password, is_active) VALUES
((SELECT client_id FROM clients WHERE email = 'test@test.com'), 'test', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true)
ON CONFLICT (username) DO NOTHING;

-- Cash Transactions (Deposits)
INSERT INTO cash_transactions (account_id, txn_type, amount, txn_date) VALUES
((SELECT account_id FROM accounts WHERE client_id = (SELECT client_id FROM clients WHERE email = 'test@test.com') ORDER BY account_id LIMIT 1), 'DEPOSIT', 100000.00, '2026-01-01')
ON CONFLICT DO NOTHING;

-- Orders and holdings intentionally omitted for this minimal single-user seed.

-- Price quotes are backfilled from Yahoo Finance during setup.
