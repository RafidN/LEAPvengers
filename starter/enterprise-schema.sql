--Drops tables and materialized views if they exist before recreating the schema
DROP MATERIALIZED VIEW IF EXISTS account_valuations;
DROP MATERIALIZED VIEW IF EXISTS latest_price_quotes;
DROP TABLE IF EXISTS price_quotes;
DROP TABLE IF EXISTS cash_transactions;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS holdings;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS clients;
DROP TABLE IF EXISTS instruments;
--Clients have basic personal information and a unique email address
CREATE TABLE clients (
    client_id             SERIAL PRIMARY KEY,
    first_name            TEXT NOT NULL,
    last_name             TEXT NOT NULL,
    email                 TEXT NOT NULL UNIQUE
);
-- Instruments are the financial products that can be traded, such as stocks, bonds, funds, and cash equivalents. This table will store their basic details.
CREATE TABLE instruments (
    instrument_id  SERIAL PRIMARY KEY,
    ticker         TEXT NOT NULL UNIQUE,
    instrument_name           TEXT NOT NULL,
    asset_class    TEXT NOT NULL CHECK (asset_class IN ('Equity', 'Bond', 'Fund', 'Cash')),
);
-- Accounts represent the different trading accounts held by clients. Each account has a cash balance.
CREATE TABLE accounts (
    account_id    SERIAL PRIMARY KEY,
    client_id     INTEGER NOT NULL REFERENCES clients(client_id),
    opened_date   DATE NOT NULL,
    balance       NUMERIC(14,4) NOT NULL DEFAULT 0 -- cash balance available to trade/withdraw
);
-- Index to quickly look up accounts by client_id for faster queries on client accounts
CREATE INDEX accounts_client_id_idx ON accounts (client_id);
-- Holdings represent the quantity of each instrument held in an account at a specific point in time. This table helps track the portfolio composition of each account.
CREATE TABLE holdings (
    holding_id     SERIAL PRIMARY KEY,
    account_id     INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id  INTEGER NOT NULL REFERENCES instruments(instrument_id),
    quantity       NUMERIC(14,4) NOT NULL,
    as_of_date     DATE NOT NULL,
    UNIQUE (account_id, instrument_id) -- one running position per instrument per account at any given time, updated whenever a new order is filled
);

-- Indexes to quickly look up holdings by account_id and instrument_id for faster queries on positions
CREATE INDEX holdings_account_id_idx ON holdings (account_id);
CREATE INDEX holdings_instrument_id_idx ON holdings (instrument_id);
-- Orders represent the buy/sell instructions placed by clients for specific instruments. Each order has a type, quantity, price, and status.
-- This table can be used to audit the history of orders placed by clients, including their status changes and execution details.
CREATE TABLE orders (
    order_id         SERIAL PRIMARY KEY,
    account_id       INTEGER NOT NULL REFERENCES accounts(account_id),
    instrument_id    INTEGER NOT NULL REFERENCES instruments(instrument_id),
    order_type       TEXT NOT NULL CHECK (order_type IN ('BUY', 'SELL')),
    quantity         NUMERIC(14,4) NOT NULL,
    price            NUMERIC(14,4) NOT NULL,
    order_date       DATE NOT NULL,
    order_status     TEXT NOT NULL DEFAULT 'Pending' CHECK (order_status IN ('Pending', 'Filled', 'Cancelled', 'Rejected')),
    submitted_at     TIMESTAMP NOT NULL DEFAULT now(),
    executed_at      TIMESTAMP -- set once order_status reaches Filled
);
-- Indexes to quickly look up orders by account_id and instrument_id for faster queries on order history
CREATE INDEX orders_account_id_idx ON orders (account_id);
CREATE INDEX orders_instrument_id_idx ON orders (instrument_id);
-- Index for audit queries that filter/sort by submission time across all accounts
CREATE INDEX orders_submitted_at_idx ON orders (submitted_at);

-- Blocks duplicate submissions (e.g. accidental double-click/retry) by rejecting an identical
-- order for the same account+instrument+type+quantity+price within the same second.
CREATE UNIQUE INDEX orders_dedup_idx ON orders (
    account_id, instrument_id, order_type, quantity, price, date_trunc('second', submitted_at)
);

-- Keeps holdings in sync when an order is filled, instead of relying on the app to remember to
-- update both tables. Only whole fills adjust holdings
CREATE OR REPLACE FUNCTION sync_holdings_on_order_fill() RETURNS TRIGGER AS $$
DECLARE
    signed_quantity NUMERIC(14,4);
BEGIN
    IF NEW.order_status = 'Filled' AND (TG_OP = 'INSERT' OR OLD.order_status IS DISTINCT FROM 'Filled') THEN
        signed_quantity := CASE WHEN NEW.order_type = 'BUY' THEN NEW.quantity ELSE -NEW.quantity END;

        INSERT INTO holdings (account_id, instrument_id, quantity, as_of_date)
        VALUES (NEW.account_id, NEW.instrument_id, signed_quantity, CURRENT_DATE)
        ON CONFLICT (account_id, instrument_id) DO UPDATE
            SET quantity = holdings.quantity + EXCLUDED.quantity,
                as_of_date = EXCLUDED.as_of_date;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- When an order is made the database will automatically update the holdings if the order is filled, ensuring consistency between orders and holdings
CREATE TRIGGER orders_sync_holdings
    AFTER INSERT OR UPDATE OF order_status ON orders
    FOR EACH ROW
    EXECUTE FUNCTION sync_holdings_on_order_fill();
-- This table records all cash transactions for each account, including deposits, withdrawals, and dividends. It helps track the cash flow and balance of each account.
CREATE TABLE cash_transactions (
    cash_transaction_id  SERIAL PRIMARY KEY,
    account_id           INTEGER NOT NULL REFERENCES accounts(account_id),
    txn_type             TEXT NOT NULL CHECK (txn_type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount               NUMERIC(14,4) NOT NULL,
    txn_date             DATE NOT NULL
);
-- Index to quickly look up cash transactions by account_id for faster queries on account cash flow
CREATE INDEX cash_transactions_account_id_idx ON cash_transactions (account_id);

-- Live market data, populated by an external feed (e.g. Alpha Vantage),
-- kept separate from the static instrument reference data above.
CREATE TABLE price_quotes (
    price_quote_id   SERIAL PRIMARY KEY,
    instrument_id     INTEGER NOT NULL REFERENCES instruments(instrument_id),
    price             NUMERIC(14,4) NOT NULL,
    volume            BIGINT,
    quote_timestamp   TIMESTAMP NOT NULL, -- timestamp reported by the data provider
    fetched_at        TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (instrument_id, quote_timestamp)
);

CREATE INDEX price_quotes_instrument_id_idx ON price_quotes (instrument_id);

-- Latest known price per instrument, used to value holdings.
-- Materialized (not a plain view) since this is read frequently; refresh on a schedule/after
-- price_quotes loads with a cooldown, not on every page request.
CREATE MATERIALIZED VIEW latest_price_quotes AS
SELECT DISTINCT ON (instrument_id) instrument_id, price, quote_timestamp
FROM price_quotes
ORDER BY instrument_id, quote_timestamp DESC;

-- Unique index required for REFRESH MATERIALIZED VIEW CONCURRENTLY (non-blocking refresh).
CREATE UNIQUE INDEX latest_price_quotes_instrument_id_idx ON latest_price_quotes (instrument_id);

-- Per-account market value: each holding valued at its instrument's latest price, summed.
CREATE MATERIALIZED VIEW account_valuations AS
SELECT h.account_id, SUM(h.quantity * lpq.price) AS total_value
FROM holdings h
JOIN latest_price_quotes lpq ON lpq.instrument_id = h.instrument_id
GROUP BY h.account_id;

CREATE UNIQUE INDEX account_valuations_account_id_idx ON account_valuations (account_id);
