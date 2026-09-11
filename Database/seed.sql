DELETE FROM CLIENTS;
DELETE FROM INSTRUMENTS;
DELETE FROM ACCOUNTS;
DELETE FROM USERS;
DELETE FROM CASH_TRANSACTIONS;
DELETE FROM ORDERS;
DELETE FROM HOLDINGS;

-- Clients
INSERT INTO clients (first_name, last_name, email) VALUES
('Mark', 'Bounheuangvilay', 'mark.bounheuangvilay@example.com'),
('Rafid', 'Nasery', 'rafid.nasery@example.com'),
('Bryan', 'Nguyen', 'bryan.nguyen@example.com'),
('Paula', 'Agyeman', 'paula.agyeman@example.com'),
('Samuel', 'Onwuwkeme', 'samuel.onwuwkeme@example.com')
ON CONFLICT (email) DO NOTHING;

-- Instruments (Common stocks, ETFs, and bonds)
INSERT INTO instruments (ticker, instrument_name, asset_class) VALUES
('AAPL', 'Apple Inc.', 'Equity'),
('GOOGL', 'Google Inc.', 'Equity'),
('MSFT', 'Microsoft Corporation', 'Equity'),
('TSLA', 'Tesla Inc.', 'Equity'),
('AMZN', 'Amazon Inc.', 'Equity'),
('META', 'Meta Platforms Inc.', 'Equity'),
('NVDA', 'NVIDIA Corporation', 'Equity'),
('JPM', 'JPMorgan Chase & Co.', 'Equity'),
('XUS', 'Vanguard U.S. Total Market Index ETF', 'Fund'),
('VFV', 'Vanguard U.S. Dividend Appreciation Index ETF', 'Fund'),
('BND', 'Vanguard Total Bond Market ETF', 'Bond'),
('CAD', 'Canadian Dollar', 'Cash')
ON CONFLICT (ticker) DO NOTHING;

-- Accounts
INSERT INTO accounts (client_id, opened_date, balance) VALUES
(1, '2023-01-15', 50000.00),
(1, '2023-06-20', 25000.00),
(2, '2023-03-10', 75000.00),
(3, '2023-05-05', 100000.00),
(4, '2023-08-12', 45000.00),
(5, '2024-01-20', 60000.00)
ON CONFLICT DO NOTHING;

-- Users (authentication)
-- Password hashes for: Mark123!, Rafid456@, Bryan789#, Paula321$, Samuel654%
INSERT INTO users (client_id, username, password, is_active) VALUES
(1, 'mbounheuangvilay', '$2a$10$0cEYBzZPmJVmTLmOsmBpH.iLqBVgJX9K/LU9XJ9K2X3jCvU8e2nKK', true),
(2, 'rnasery', '$2a$10$X2aJJpZH0r3NKLsD2vN5pOn1tM8zP9T5Q1V2R3S4T5U6V7W8X9Y0Z', true),
(3, 'bnguyen', '$2a$10$NzOmLkJiHgFeDcBaZ9yXwO0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5', true),
(4, 'pagyeman', '$2a$10$P9yXwOvUtSrQpOnMlKjIhG0F1E2D3C4B5A6Z7Y8X9W0V1U2T3S4R', true),
(5, 'sonwuwkeme', '$2a$10$C4b3A2z1Y0X9W8V7U6T5S4R3Q2P1O0N9M8L7K6J5I4H3G2F1E0D9', true)
ON CONFLICT (username) DO NOTHING;

-- Cash Transactions (Deposits)
INSERT INTO cash_transactions (account_id, txn_type, amount, txn_date) VALUES
(1, 'DEPOSIT', 50000.00, '2023-01-15'),
(1, 'DEPOSIT', 10000.00, '2024-06-01'),
(2, 'DEPOSIT', 25000.00, '2023-06-20'),
(3, 'DEPOSIT', 75000.00, '2023-03-10'),
(3, 'DEPOSIT', 15000.00, '2024-01-15'),
(4, 'DEPOSIT', 100000.00, '2023-05-05'),
(5, 'DEPOSIT', 45000.00, '2023-08-12'),
(6, 'DEPOSIT', 60000.00, '2024-01-20')
ON CONFLICT DO NOTHING;

-- Orders (BUY and SELL orders with various statuses)
INSERT INTO orders (account_id, instrument_id, order_type, quantity, price, order_date, order_status) VALUES
-- Account 1 orders
(1, 1, 'BUY', 50, 180.25, '2023-02-01', 'Filled'),
(1, 3, 'BUY', 30, 380.50, '2023-02-15', 'Filled'),
(1, 5, 'BUY', 20, 3250.75, '2023-03-01', 'Filled'),
(1, 9, 'BUY', 100, 45.30, '2023-04-10', 'Filled'),
(1, 1, 'SELL', 10, 195.00, '2024-06-15', 'Filled'),
(1, 2, 'BUY', 15, 140.20, '2024-07-01', 'Pending'),
(1, 4, 'BUY', 5, 250.00, '2024-08-15', 'Canceled'),

-- Account 2 orders
(2, 1, 'BUY', 75, 175.00, '2023-04-05', 'Filled'),
(2, 7, 'BUY', 40, 520.10, '2023-05-12', 'Filled'),
(2, 11, 'BUY', 200, 80.45, '2023-06-20', 'Filled'),
(2, 6, 'BUY', 25, 220.30, '2024-08-20', 'Pending'),

-- Account 3 orders
(3, 1, 'BUY', 100, 150.00, '2023-03-20', 'Filled'),
(3, 3, 'BUY', 60, 350.00, '2023-04-10', 'Filled'),
(3, 5, 'BUY', 30, 3000.00, '2023-05-15', 'Filled'),
(3, 9, 'BUY', 150, 42.00, '2023-06-01', 'Filled'),
(3, 10, 'BUY', 100, 55.75, '2023-07-10', 'Filled'),
(3, 2, 'BUY', 25, 135.00, '2024-01-20', 'Filled'),
(3, 1, 'SELL', 30, 200.00, '2024-07-01', 'Filled'),

-- Account 4 orders
(4, 1, 'BUY', 120, 155.00, '2023-06-05', 'Filled'),
(4, 3, 'BUY', 80, 360.00, '2023-07-12', 'Filled'),
(4, 7, 'BUY', 50, 500.00, '2023-08-20', 'Filled'),
(4, 4, 'BUY', 40, 245.00, '2024-05-10', 'Filled'),

-- Account 5 orders
(5, 1, 'BUY', 60, 170.00, '2023-09-15', 'Filled'),
(5, 9, 'BUY', 200, 40.00, '2023-10-01', 'Filled'),

-- Account 6 orders
(6, 1, 'BUY', 80, 165.00, '2024-02-10', 'Filled'),
(6, 3, 'BUY', 50, 370.00, '2024-03-05', 'Filled'),
(6, 5, 'BUY', 25, 3100.00, '2024-04-15', 'Filled')
ON CONFLICT DO NOTHING;

-- Holdings (Positions based on filled orders)
INSERT INTO holdings (account_id, instrument_id, quantity, as_of_date) VALUES
-- Account 1 holdings
(1, 1, 40, CURRENT_DATE),  
(1, 3, 30, CURRENT_DATE),
(1, 5, 20, CURRENT_DATE),
(1, 9, 100, CURRENT_DATE),

-- Account 2 holdings
(2, 1, 75, CURRENT_DATE),
(2, 7, 40, CURRENT_DATE),
(2, 11, 200, CURRENT_DATE),

-- Account 3 holdings
(3, 1, 70, CURRENT_DATE),  
(3, 3, 60, CURRENT_DATE),
(3, 5, 30, CURRENT_DATE),
(3, 9, 150, CURRENT_DATE),
(3, 10, 100, CURRENT_DATE),
(3, 2, 25, CURRENT_DATE),

-- Account 4 holdings
(4, 1, 120, CURRENT_DATE),
(4, 3, 80, CURRENT_DATE),
(4, 7, 50, CURRENT_DATE),
(4, 4, 40, CURRENT_DATE),

-- Account 5 holdings
(5, 1, 60, CURRENT_DATE),
(5, 9, 200, CURRENT_DATE),

-- Account 6 holdings
(6, 1, 80, CURRENT_DATE),
(6, 3, 50, CURRENT_DATE),
(6, 5, 25, CURRENT_DATE)
ON CONFLICT (account_id, instrument_id) DO UPDATE SET quantity = EXCLUDED.quantity;

-- Price Quotes (Recent market data)
-- AAPL
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(1, 225.50, 52000000, NOW() - INTERVAL '2 hours'),
(1, 225.75, 48000000, NOW() - INTERVAL '1 hour'),
(1, 226.10, 42000000, NOW());

-- GOOGL
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(2, 165.80, 28000000, NOW() - INTERVAL '2 hours'),
(2, 166.20, 30000000, NOW() - INTERVAL '1 hour'),
(2, 167.05, 25000000, NOW());

-- MSFT
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(3, 445.30, 35000000, NOW() - INTERVAL '2 hours'),
(3, 446.15, 32000000, NOW() - INTERVAL '1 hour'),
(3, 447.80, 28000000, NOW());

-- TSLA
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(4, 278.50, 120000000, NOW() - INTERVAL '2 hours'),
(4, 280.25, 115000000, NOW() - INTERVAL '1 hour'),
(4, 282.10, 105000000, NOW());

-- AMZN
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(5, 3650.80, 45000000, NOW() - INTERVAL '2 hours'),
(5, 3665.20, 42000000, NOW() - INTERVAL '1 hour'),
(5, 3680.50, 38000000, NOW());

-- META
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(6, 520.40, 25000000, NOW() - INTERVAL '2 hours'),
(6, 522.10, 23000000, NOW() - INTERVAL '1 hour'),
(6, 525.75, 20000000, NOW());

-- NVDA
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(7, 875.30, 55000000, NOW() - INTERVAL '2 hours'),
(7, 878.50, 52000000, NOW() - INTERVAL '1 hour'),
(7, 882.15, 48000000, NOW());

-- JPM
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(8, 198.75, 18000000, NOW() - INTERVAL '2 hours'),
(8, 199.50, 16000000, NOW() - INTERVAL '1 hour'),
(8, 200.25, 14000000, NOW());

-- XUS
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(9, 48.65, 5000000, NOW() - INTERVAL '2 hours'),
(9, 48.72, 4800000, NOW() - INTERVAL '1 hour'),
(9, 48.85, 4500000, NOW());

-- VFV
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(10, 58.20, 3000000, NOW() - INTERVAL '2 hours'),
(10, 58.35, 2900000, NOW() - INTERVAL '1 hour'),
(10, 58.50, 2700000, NOW());

-- BND
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(11, 82.10, 8000000, NOW() - INTERVAL '2 hours'),
(11, 82.15, 7800000, NOW() - INTERVAL '1 hour'),
(11, 82.20, 7500000, NOW());

-- CAD
INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp) VALUES
(12, 1.35, 500000000, NOW() - INTERVAL '2 hours'),
(12, 1.3505, 490000000, NOW() - INTERVAL '1 hour'),
(12, 1.351, 480000000, NOW())
ON CONFLICT (instrument_id, quote_timestamp) DO NOTHING;

-- Refresh materialized views
REFRESH MATERIALIZED VIEW latest_price_quotes;
REFRESH MATERIALIZED VIEW account_valuations;
