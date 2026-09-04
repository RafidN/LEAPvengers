# Setting up `enterprise-schema.sql` in PostgreSQL

## Prerequisites
- PostgreSQL installed locally (or access to a running instance).
- `psql` available on your PATH (ships with the PostgreSQL install).

## 1. Create a database

From inside `psql` (connected to any existing database, e.g. `postgres`):
```sql
CREATE DATABASE [NAME];
```

## 2. Load the schema

From inside `psql`, after connecting to the [NAME] database (`\c [NAME]`):
```
\i 'starter/enterprise-schema.sql'
```

## What the script does

The file is fully re-runnable: it starts with `DROP TABLE IF EXISTS` / `DROP MATERIALIZED VIEW IF EXISTS`
statements (in dependency order) before recreating everything, so you can reload it at any time to reset
your local database to a clean state.

It creates, in order:
- **Tables**: `clients`, `instruments`, `accounts`, `holdings`, `orders`, `cash_transactions`, `price_quotes`.
- **Indexes**: one per foreign key column, plus a couple of
  uniqueness/dedup constraints (e.g. `orders` rejects duplicate submissions in the same second).
- **A trigger** (`orders_sync_holdings`): automatically updates `holdings` whenever an order's status
  changes to `'Filled'`, so you don't need application code to keep the two in sync.
- **Materialized views**: `latest_price_quotes` (newest price per instrument) and `account_valuations`
  (each account's total holdings value). These are snapshots, not live queries — they need refreshing.

## Refreshing the materialized views

After loading new rows into `price_quotes`, refresh the views so `account_valuations` reflects the new
prices. Both views have a unique index, so this can run without blocking reads:
```sql
REFRESH MATERIALIZED VIEW CONCURRENTLY latest_price_quotes;
REFRESH MATERIALIZED VIEW CONCURRENTLY account_valuations;
```
This should be triggered by whatever job/service fetches new prices (e.g. from Alpha Vantage), not on
every page load — see the comments above the view definitions in `enterprise-schema.sql`.

## Verifying it worked

```
\dt        -- list tables
\dm        -- list materialized views
\d orders  -- inspect a table's columns, constraints, and indexes
```
