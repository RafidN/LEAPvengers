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

-- Orders and holdings intentionally omitted for this minimal single-user seed.

-- Price quotes are backfilled from Yahoo Finance during setup.
