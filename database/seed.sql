DELETE FROM price_quotes;
DELETE FROM cash_transactions;
DELETE FROM holdings;
DELETE FROM orders;
DELETE FROM users;
DELETE FROM accounts;
DELETE FROM instruments;
DELETE FROM clients;

-- Clients
INSERT INTO clients (first_name, last_name, email) VALUES
('Test', 'User', 'test@test.com'),
('Paula', 'Agyeman', 'paula.agyeman@lol.com'),
('Rafid', 'Nasery', 'rafid.nasery@lol.com'),
('Sam', 'Onukweme', 'sam.onukweme@lol.com'),
('Bryan', 'Nguyen', 'bryan.nguyen@lol.com'),
('Mark', 'Bounheuangvilay', 'mark.bounheuangvilay@lol.com')
ON CONFLICT (email) DO NOTHING;

-- Instruments (Common stocks, ETFs, bonds, crypto, and FX)
INSERT INTO instruments (ticker, instrument_name, asset_class) VALUES
('NFLX', 'Netflix, Inc.', 'Equity'),
('AMD', 'Advanced Micro Devices, Inc.', 'Equity'),
('ORCL', 'Oracle Corporation', 'Equity'),
('SAP', 'SAP SE', 'Equity'),
('ASML', 'ASML Holding N.V.', 'Equity'),
('SHEL', 'Shell plc', 'Equity'),
('NVO', 'Novo Nordisk A/S', 'Equity'),
('RELIANCE', 'Reliance Industries Limited', 'Equity'),
('TCS', 'Tata Consultancy Services Limited', 'Equity'),
('HDFCBANK', 'HDFC Bank Limited', 'Equity'),
('BTCUSD', 'Bitcoin / US Dollar', 'Cash'),
('ETHUSD', 'Ethereum / US Dollar', 'Cash'),
('SOLUSD', 'Solana / US Dollar', 'Cash'),
('EURUSD', 'Euro / US Dollar', 'Cash'),
('GBPUSD', 'British Pound / US Dollar', 'Cash'),
('USDJPY', 'US Dollar / Japanese Yen', 'Cash')
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
INSERT INTO users (client_id, username, password, is_active) VALUES
((SELECT client_id FROM clients WHERE email = 'test@test.com'), 'test', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true),
((SELECT client_id FROM clients WHERE email = 'paula.agyeman@lol.com'), 'paula', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true),
((SELECT client_id FROM clients WHERE email = 'rafid.nasery@lol.com'), 'rafid', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true),
((SELECT client_id FROM clients WHERE email = 'sam.onukweme@lol.com'), 'sam', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true),
((SELECT client_id FROM clients WHERE email = 'bryan.nguyen@lol.com'), 'bryan', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true),
((SELECT client_id FROM clients WHERE email = 'mark.bounheuangvilay@lol.com'), 'mark', '$2a$10$tLwtTP4HxMzpogByQUM4eOIVrG02.NIzzGpoDz2EviysE/vgF2bci', true)
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

-- Price quotes for current instrument seed data
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
((SELECT instrument_id FROM instruments WHERE ticker = 'NFLX'), 690.25, 28000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'AMD'), 198.40, 65000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'ORCL'), 182.60, 12000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'SAP'), 235.80, 4000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'ASML'), 1015.35, 2200000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'SHEL'), 73.45, 9000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'NVO'), 141.20, 5500000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'RELIANCE'), 3000.50, 10000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'TCS'), 4300.75, 4200000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'HDFCBANK'), 1785.30, 6000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'BTCUSD'), 98500.00, 25000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'ETHUSD'), 3650.00, 18000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'SOLUSD'), 210.50, 12000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'EURUSD'), 1.0875, 1500000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'GBPUSD'), 1.2740, 1200000000, NOW()),
((SELECT instrument_id FROM instruments WHERE ticker = 'USDJPY'), 154.8500, 1400000000, NOW())
ON CONFLICT (instrument_id, quote_timestamp) DO NOTHING;

-- Refresh materialized views
REFRESH MATERIALIZED VIEW latest_price_quotes;
REFRESH MATERIALIZED VIEW account_valuations;
