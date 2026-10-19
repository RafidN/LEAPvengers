-- Limit and stop orders, for databases created before they existed. enterprise-schema.sql already has all of
-- this, so a new database doesn't need it. Safe to run more than once: the init-db job
-- (scripts/docker-entrypoint.sh) runs every migration on each deployment.
--   psql -v ON_ERROR_STOP=1 -f database/migrations/001_limit_and_stop_orders.sql
SET client_min_messages = warning;
BEGIN;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_kind TEXT NOT NULL DEFAULT 'MARKET';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS trigger_price NUMERIC(14,4);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS triggered_at TIMESTAMP;

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_order_kind_check;
ALTER TABLE orders ADD CONSTRAINT orders_order_kind_check CHECK (order_kind IN ('MARKET', 'LIMIT', 'STOP'));
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_trigger_price_check;
ALTER TABLE orders ADD CONSTRAINT orders_trigger_price_check CHECK (trigger_price > 0);
ALTER TABLE orders DROP CONSTRAINT IF EXISTS trigger_price_for_limit_and_stop;
ALTER TABLE orders ADD CONSTRAINT trigger_price_for_limit_and_stop CHECK ((order_kind = 'MARKET') = (trigger_price IS NULL));

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_order_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_order_status_check
    CHECK (order_status IN ('SUBMITTED', 'WORKING', 'ACCEPTED', 'FILLED', 'REJECTED', 'FAILED', 'CANCELED'));
ALTER TABLE orders DROP CONSTRAINT IF EXISTS rejection_reason_required;
ALTER TABLE orders ADD CONSTRAINT rejection_reason_required CHECK (
    (order_status IN ('REJECTED', 'FAILED', 'CANCELED') AND rejection_reason IS NOT NULL)
    OR order_status NOT IN ('REJECTED', 'FAILED', 'CANCELED')
);

ALTER TABLE order_events DROP CONSTRAINT IF EXISTS order_events_from_status_check;
ALTER TABLE order_events ADD CONSTRAINT order_events_from_status_check CHECK (from_status IS NULL
    OR from_status IN ('SUBMITTED', 'WORKING', 'FILLED', 'CANCELED', 'ACCEPTED', 'REJECTED', 'FAILED'));
ALTER TABLE order_events DROP CONSTRAINT IF EXISTS order_events_to_status_check;
ALTER TABLE order_events ADD CONSTRAINT order_events_to_status_check
    CHECK (to_status IN ('SUBMITTED', 'WORKING', 'FILLED', 'CANCELED', 'ACCEPTED', 'REJECTED', 'FAILED'));

-- The same function as in enterprise-schema.sql
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
    IF NEW.triggered_at IS DISTINCT FROM OLD.triggered_at
       AND NOT (OLD.order_status = 'WORKING' AND NEW.order_status = 'ACCEPTED' AND OLD.triggered_at IS NULL) THEN
        RAISE EXCEPTION 'order % : triggered_at is only written when a working order is triggered', OLD.order_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX IF NOT EXISTS orders_working_idx ON orders (instrument_id) WHERE order_status = 'WORKING';

DROP INDEX IF EXISTS orders_dedup_idx;
CREATE UNIQUE INDEX orders_dedup_idx ON orders (
    account_id, instrument_id, order_type, quantity, price, order_kind, COALESCE(trigger_price, 0),
    date_trunc('second', submitted_at)
);

COMMIT;
