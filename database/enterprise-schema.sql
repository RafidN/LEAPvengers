--Drops tables and materialized views if they exist before recreating the schema
DROP SCHEMA IF EXISTS reporting CASCADE;
DROP MATERIALIZED VIEW IF EXISTS account_valuations;
DROP MATERIALIZED VIEW IF EXISTS latest_price_quotes;
DROP TABLE IF EXISTS price_quotes CASCADE;
DROP TABLE IF EXISTS order_disputes CASCADE;
DROP TABLE IF EXISTS cash_transactions CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS order_events CASCADE;
DROP TABLE IF EXISTS holdings CASCADE;
DROP TABLE IF EXISTS accounts CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS clients CASCADE;
DROP TABLE IF EXISTS instruments CASCADE;
-- Removed trigger function; dropping it keeps databases built from older versions of this file in step
DROP FUNCTION IF EXISTS sync_holdings_on_order_fill() CASCADE;
--Clients have basic personal information and a unique email address
CREATE TABLE clients (
    client_id             SERIAL PRIMARY KEY,
    first_name            TEXT NOT NULL,
    last_name             TEXT NOT NULL,
    email                 TEXT NOT NULL UNIQUE,
    segment               TEXT NOT NULL DEFAULT 'Active'
                              CHECK (segment IN ('Dormant', 'Core', 'Active', 'Premier'))
);

-- Instruments are the financial products that can be traded, such as stocks, bonds, funds, cash equivalents, FX pairs and crypto. This table will store their basic details.
CREATE TABLE instruments (
    instrument_id  SERIAL PRIMARY KEY,
    ticker         TEXT NOT NULL UNIQUE,
    instrument_name           TEXT NOT NULL,
    asset_class    TEXT NOT NULL CHECK (asset_class IN ('Equity', 'Bond', 'Fund', 'Cash', 'FX', 'Crypto')),
    market         TEXT NOT NULL CHECK (market IN ('US', 'UK', 'IN')), -- exchange/market the instrument is quoted on; lets Indian stocks be told apart from US/UK
    -- The trading desk can suspend an instrument (e.g. pending news or a corporate action). Orders in a
    -- suspended instrument are rejected even while its market is open (BR-05).
    tradable       BOOLEAN NOT NULL DEFAULT true,
    -- How far behind real time our market data for this instrument is allowed to be, in minutes. Yahoo's free
    -- feed is real time for US stocks, FX and crypto but 15 minutes behind for London and NSE; a licensed
    -- real-time feed would set this back to 0. The "current price" rule allows this much extra age, and the
    -- order ticket and audit trail say the price is delayed.
    quote_delay_minutes INTEGER NOT NULL DEFAULT 0 CHECK (quote_delay_minutes >= 0)
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

-- Users table for authentication, linked to clients
-- A TRADER belongs to one client and can access all of that client's accounts.
-- OPS and ANALYST users are internal staff, so they have no client_id.
CREATE TABLE users (
    user_id               SERIAL PRIMARY KEY,
    client_id             INTEGER REFERENCES clients(client_id), -- NULL for internal (OPS/ANALYST) users
    username              TEXT NOT NULL UNIQUE,
    first_name            TEXT,
    last_name             TEXT,
    password              VARCHAR(255) NOT NULL,
    role                  TEXT NOT NULL DEFAULT 'TRADER' CHECK (role IN ('TRADER', 'OPS', 'ANALYST')),
    is_active             BOOLEAN DEFAULT true,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

-- Index for fast username lookups during login
CREATE INDEX users_username_idx ON users (username);
-- Index for finding all users of a client
CREATE INDEX users_client_id_idx ON users (client_id);
-- Usernames and client emails are unique regardless of case ("Sam" and "sam" are the same person).
-- AuthService checks first; these stop two sign-ups racing past the check at the same moment.
CREATE UNIQUE INDEX users_username_lower_idx ON users (lower(username));
CREATE UNIQUE INDEX clients_email_lower_idx ON clients (lower(email));
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
    quantity         NUMERIC(14,4) NOT NULL CHECK (quantity > 0),
    price            NUMERIC(14,4) NOT NULL CHECK (price > 0),
    order_date       DATE NOT NULL,
    order_status     TEXT NOT NULL DEFAULT 'SUBMITTED'
                     CHECK (order_status IN ('SUBMITTED', 'WORKING', 'ACCEPTED', 'FILLED', 'REJECTED', 'FAILED', 'CANCELED')),
    rejection_reason TEXT, -- why a REJECTED, FAILED or CANCELED order ended
    execution_price  NUMERIC(14,4),
    submitted_at     TIMESTAMP NOT NULL DEFAULT now(),
    executed_at      TIMESTAMP, -- set by application/business logic when the order is executed
    -- MARKET fills now at the live price. LIMIT and STOP wait as WORKING until the price reaches trigger_price:
    -- a limit buy at or below it, a limit sell at or above it, a stop sell (stop-loss) at or below it and a
    -- stop buy at or above it. price is still the live price when the order was placed.
    order_kind       TEXT NOT NULL DEFAULT 'MARKET' CHECK (order_kind IN ('MARKET', 'LIMIT', 'STOP')),
    trigger_price    NUMERIC(14,4) CHECK (trigger_price > 0),
    triggered_at     TIMESTAMP, -- when a WORKING order's price was reached and it went on to be filled

    CONSTRAINT rejection_reason_required CHECK (
        (order_status IN ('REJECTED', 'FAILED', 'CANCELED') AND rejection_reason IS NOT NULL)
        OR order_status NOT IN ('REJECTED', 'FAILED', 'CANCELED')
    ),
    CONSTRAINT trigger_price_for_limit_and_stop CHECK ((order_kind = 'MARKET') = (trigger_price IS NULL)),
    CONSTRAINT execution_price_filled_only CHECK (
        (order_status = 'FILLED' AND execution_price IS NOT NULL)
        OR (order_status != 'FILLED' AND execution_price IS NULL)
    )
);
CREATE TABLE order_events(
    event_id        SERIAL PRIMARY KEY,
    order_id        INTEGER NOT NULL REFERENCES orders(order_id),
    created_by      INTEGER REFERENCES users(user_id),
    from_status     TEXT CHECK (from_status IS NULL OR from_status IN ('SUBMITTED', 'WORKING', 'FILLED', 'CANCELED', 'ACCEPTED', 'REJECTED', 'FAILED')),
    to_status       TEXT NOT NULL CHECK (to_status IN ('SUBMITTED', 'WORKING', 'FILLED', 'CANCELED', 'ACCEPTED', 'REJECTED', 'FAILED')),
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    details         TEXT, -- optional field to annotate the event with additional context
    -- The quote this step was priced against (BR-15: how was it priced?). A copy, not a reference, so the
    -- record stands on its own: price, the quote's own timestamp and where it came from (price_quotes.source).
    quote_price     NUMERIC(14,4),
    quote_time      TIMESTAMP,
    quote_source    TEXT,
    CHECK (from_status IS DISTINCT FROM to_status)
);

-- Makes order_events append-only by rejecting any attempt to update or delete an existing event.
CREATE OR REPLACE FUNCTION prevent_order_event_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'order_events is append-only; % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER order_events_append_only
    BEFORE UPDATE OR DELETE ON order_events
    FOR EACH ROW
    EXECUTE FUNCTION prevent_order_event_mutation();

-- Trade records can't be altered after the fact (BR-14, section 9.4). An order may only move forward through
-- its lifecycle, the fill details may only be written by the fill, and what the client asked for (account,
-- instrument, side, quantity, kind, prices, when) never changes. Orders are never deleted.
--   SUBMITTED -> ACCEPTED or REJECTED      a market order, checked
--   SUBMITTED -> WORKING                   a limit or stop order, checked and waiting for its price
--   WORKING   -> ACCEPTED or CANCELED      its price was reached (triggered_at), or the client canceled it
--   ACCEPTED  -> FILLED or FAILED
-- Moving a working order to a new price is a cancel and a new order, so the old price stays on record.
-- FILLED, REJECTED, FAILED and CANCELED are final.
-- Kept in step with database/migrations/001_limit_and_stop_orders.sql, which upgrades existing databases.
CREATE OR REPLACE FUNCTION protect_order_record() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'orders are permanent records; DELETE of order % is not allowed', OLD.order_id;
    END IF;
    IF NEW.order_id IS DISTINCT FROM OLD.order_id
       OR NEW.account_id IS DISTINCT FROM OLD.account_id
       OR NEW.instrument_id IS DISTINCT FROM OLD.instrument_id
       OR NEW.order_type IS DISTINCT FROM OLD.order_type
       OR NEW.quantity IS DISTINCT FROM OLD.quantity
       OR NEW.price IS DISTINCT FROM OLD.price
       OR NEW.order_date IS DISTINCT FROM OLD.order_date
       OR NEW.submitted_at IS DISTINCT FROM OLD.submitted_at
       OR NEW.order_kind IS DISTINCT FROM OLD.order_kind
       OR NEW.trigger_price IS DISTINCT FROM OLD.trigger_price THEN
        RAISE EXCEPTION 'order % : what was ordered can never change', OLD.order_id;
    END IF;
    IF NEW.order_status = OLD.order_status THEN
        -- Saving an order without changing it is harmless, but nothing else may change while it waits
        IF NEW.rejection_reason IS DISTINCT FROM OLD.rejection_reason
           OR NEW.execution_price IS DISTINCT FROM OLD.execution_price
           OR NEW.executed_at IS DISTINCT FROM OLD.executed_at
           OR NEW.triggered_at IS DISTINCT FROM OLD.triggered_at THEN
            RAISE EXCEPTION 'order % is % ; its outcome can only be written by a status change', OLD.order_id, OLD.order_status;
        END IF;
        RETURN NEW;
    END IF;
    IF NOT ((OLD.order_status = 'SUBMITTED' AND NEW.order_status IN ('ACCEPTED', 'REJECTED', 'WORKING'))
         OR (OLD.order_status = 'WORKING' AND NEW.order_status IN ('ACCEPTED', 'CANCELED'))
         OR (OLD.order_status = 'ACCEPTED' AND NEW.order_status IN ('FILLED', 'FAILED'))) THEN
        RAISE EXCEPTION 'order % cannot go from % to % ; orders only move SUBMITTED -> ACCEPTED, REJECTED or WORKING, WORKING -> ACCEPTED or CANCELED, then ACCEPTED -> FILLED or FAILED',
            OLD.order_id, OLD.order_status, NEW.order_status;
    END IF;
    -- When a working order was triggered is written once, by that step
    IF NEW.triggered_at IS DISTINCT FROM OLD.triggered_at
       AND NOT (OLD.order_status = 'WORKING' AND NEW.order_status = 'ACCEPTED' AND OLD.triggered_at IS NULL) THEN
        RAISE EXCEPTION 'order % : triggered_at is only written when a working order is triggered', OLD.order_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER orders_protect_record
    BEFORE UPDATE OR DELETE ON orders
    FOR EACH ROW
    EXECUTE FUNCTION protect_order_record();

-- Indexes to quickly look up orders by account_id and instrument_id for faster queries on order history
CREATE INDEX orders_account_id_idx ON orders (account_id);
CREATE INDEX orders_instrument_id_idx ON orders (instrument_id);
-- Index for audit queries that filter/sort by submission time across all accounts
CREATE INDEX orders_submitted_at_idx ON orders (submitted_at);
-- Lets the restart recovery sweep (OrderRecoveryService) find unfinished orders without scanning history.
CREATE INDEX orders_unfinished_idx ON orders (submitted_at) WHERE order_status IN ('SUBMITTED', 'ACCEPTED');
-- Supports client segmentation queries that count recent filled orders per account.
CREATE INDEX orders_account_status_submitted_at_idx ON orders (account_id, order_status, submitted_at DESC);
-- Lets the working-order monitor (WorkingOrderService) check only the orders still waiting for a price.
CREATE INDEX orders_working_idx ON orders (instrument_id) WHERE order_status = 'WORKING';

-- Blocks duplicate submissions (e.g. accidental double-click/retry) by rejecting an identical
-- order for the same account+instrument+type+quantity+price+kind+trigger within the same second.
CREATE UNIQUE INDEX orders_dedup_idx ON orders (
    account_id, instrument_id, order_type, quantity, price, order_kind, COALESCE(trigger_price, 0),
    date_trunc('second', submitted_at)
);

-- Holdings and cash are not moved by a trigger: OrderExecutionService updates them in Java, in the
-- same transaction as the order, so every fill is applied exactly once.

-- This table records all cash movements for each account: deposits, withdrawals, and the cash paid for a
-- filled buy or received for a filled sell. It helps track the cash flow and balance of each account.
CREATE TABLE cash_transactions (
    cash_transaction_id  SERIAL PRIMARY KEY,
    account_id           INTEGER NOT NULL REFERENCES accounts(account_id),
    txn_type             TEXT NOT NULL CHECK (txn_type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY', 'SELL')),
    amount               NUMERIC(14,4) NOT NULL,
    txn_date             DATE NOT NULL,
    order_id             INTEGER REFERENCES orders(order_id), -- the filled order, for BUY and SELL rows
    created_at           TIMESTAMP NOT NULL DEFAULT now(), -- when the cash moved (BR-14: attributable to a time)
    CHECK ((txn_type IN ('BUY', 'SELL')) = (order_id IS NOT NULL))
);
-- Index to quickly look up cash transactions by account_id for faster queries on account cash flow
CREATE INDEX cash_transactions_account_id_idx ON cash_transactions (account_id);
-- At most one cash movement per order, so a fill can never be paid for twice
CREATE UNIQUE INDEX cash_transactions_order_id_idx ON cash_transactions (order_id) WHERE order_id IS NOT NULL;

-- Cash movements are the record of every change to a balance; a correction is a new movement, never an edit.
CREATE OR REPLACE FUNCTION prevent_cash_transaction_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'cash_transactions is append-only; % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER cash_transactions_append_only
    BEFORE UPDATE OR DELETE ON cash_transactions
    FOR EACH ROW
    EXECUTE FUNCTION prevent_cash_transaction_mutation();

-- A client's dispute of one of their orders, raised from their History page and worked by Operations
-- (BR-15: answer a client dispute from the records). Like the other trade records it is never deleted, and
-- it only ever moves from OPEN to RESOLVED, with Ops' resolution, who resolved it and when.
CREATE TABLE order_disputes (
    dispute_id    SERIAL PRIMARY KEY,
    order_id      INTEGER NOT NULL REFERENCES orders(order_id),
    raised_by     INTEGER NOT NULL REFERENCES users(user_id),
    reason        TEXT NOT NULL CHECK (reason IN ('WRONG_PRICE', 'SHOULD_HAVE_FILLED', 'NOT_PLACED_BY_ME', 'OTHER')),
    message       TEXT NOT NULL CHECK (length(message) BETWEEN 1 AND 1000),
    status        TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    resolution    TEXT CHECK (resolution IS NULL OR length(resolution) BETWEEN 1 AND 1000),
    resolved_by   INTEGER REFERENCES users(user_id),
    resolved_at   TIMESTAMP,
    CHECK ((status = 'RESOLVED') = (resolution IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL))
);
-- One open dispute per order at a time; once it is resolved the client can raise another
CREATE UNIQUE INDEX order_disputes_one_open_idx ON order_disputes (order_id) WHERE status = 'OPEN';
CREATE INDEX order_disputes_status_created_idx ON order_disputes (status, created_at);

CREATE OR REPLACE FUNCTION protect_order_dispute() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'disputes are permanent records; DELETE of dispute % is not allowed', OLD.dispute_id;
    END IF;
    IF OLD.status <> 'OPEN' OR NEW.status <> 'RESOLVED'
       OR NEW.dispute_id IS DISTINCT FROM OLD.dispute_id
       OR NEW.order_id IS DISTINCT FROM OLD.order_id
       OR NEW.raised_by IS DISTINCT FROM OLD.raised_by
       OR NEW.reason IS DISTINCT FROM OLD.reason
       OR NEW.message IS DISTINCT FROM OLD.message
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'dispute % can only be resolved once, and what the client raised never changes', OLD.dispute_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER order_disputes_protect_record
    BEFORE UPDATE OR DELETE ON order_disputes
    FOR EACH ROW
    EXECUTE FUNCTION protect_order_dispute();

-- Live market data, populated by an external feed (e.g. Alpha Vantage),
-- kept separate from the static instrument reference data above.
CREATE TABLE price_quotes (
    price_quote_id   SERIAL PRIMARY KEY,
    instrument_id     INTEGER NOT NULL REFERENCES instruments(instrument_id),
    price             NUMERIC(14,4) NOT NULL,
    volume            BIGINT,
    quote_timestamp   TIMESTAMP NOT NULL, -- timestamp reported by the data provider
    fetched_at        TIMESTAMP NOT NULL DEFAULT now(),
    -- Where the price came from, so an order's pricing can be explained (BR-15):
    --   YAHOO_LIVE    a live trade price streamed from Yahoo Finance (scripts/live_quote_generator.py)
    --   YAHOO_HISTORY a historical bar from Yahoo Finance (scripts/yahoo_backfill.py)
    --   SIMULATED     made up by the quote generator's offline --source simulated mode; never a real price
    source            TEXT NOT NULL DEFAULT 'YAHOO_HISTORY' CHECK (source IN ('YAHOO_LIVE', 'YAHOO_HISTORY', 'SIMULATED')),
    UNIQUE (instrument_id, quote_timestamp)
);

-- NOTE: Redundant with the UNIQUE (instrument_id, quote_timestamp) index; omit to reduce write overhead.

-- Latest known price per instrument, used to value holdings.
-- Materialized (not a plain view) since this is read frequently; refresh on a schedule/after
-- price_quotes loads with a cooldown, not on every page request.
CREATE MATERIALIZED VIEW latest_price_quotes AS
SELECT DISTINCT ON (instrument_id) instrument_id, price, quote_timestamp, source
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

-- =====================================================================================================
-- Reporting store (BR-16). Analysts and Ops read trading activity from here, never from the live
-- trading tables above, so their queries can't slow down or lock live trading. ReportingLoadService
-- copies each FILLED order in once (a small batch every minute) and the rows never change after that,
-- so monthly figures are stable. Deliberately no foreign keys back to the live tables: this schema can
-- move to its own database or a read replica later without changing the reports.
-- =====================================================================================================
CREATE SCHEMA reporting;

-- One row per filled order, with everything reports group by already on the row.
CREATE TABLE reporting.trade_activity (
    order_id        INTEGER PRIMARY KEY,           -- the live order it came from (orders.order_id)
    executed_at     TIMESTAMP NOT NULL,
    trade_date      DATE NOT NULL,
    client_id       INTEGER NOT NULL,              -- for counting distinct active clients; never shown to analysts
    client_segment  TEXT NOT NULL,                 -- the client's segment when the trade was loaded
    instrument_id   INTEGER NOT NULL,
    ticker          TEXT NOT NULL,
    asset_class     TEXT NOT NULL,
    market          TEXT NOT NULL,
    side            TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity        NUMERIC(14,4) NOT NULL,
    price           NUMERIC(14,4) NOT NULL,        -- execution price, USD
    notional        NUMERIC(18,4) NOT NULL,        -- quantity x price, USD
    loaded_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX trade_activity_trade_date_idx ON reporting.trade_activity (trade_date);
CREATE INDEX trade_activity_executed_at_idx ON reporting.trade_activity (executed_at);
CREATE INDEX trade_activity_ticker_idx ON reporting.trade_activity (ticker);
CREATE INDEX trade_activity_segment_idx ON reporting.trade_activity (client_segment);

-- Each load, so reports can say how fresh their data is.
CREATE TABLE reporting.load_runs (
    run_id        SERIAL PRIMARY KEY,
    started_at    TIMESTAMP NOT NULL,
    finished_at   TIMESTAMP NOT NULL,
    rows_loaded   INTEGER NOT NULL
);
