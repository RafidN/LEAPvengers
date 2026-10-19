# Local Database Setup (PostgreSQL)

## Prerequisites
- PostgreSQL 12+ installed and running
- psql CLI available
- Database: `leapvengersdb`
- User: `postgres` / Password: `n3u3d4!`

## Setup Steps

### 1. Create Database
```bash
psql -h localhost -p 5432 -U postgres -c "CREATE DATABASE leapvengersdb;"
```

### 2. Run Schema
```bash
psql -h localhost -p 5432 -U postgres -d leapvengersdb -f database/enterprise-schema.sql
```

A database created before a schema change catches up with the files in `database/migrations`, oldest first, without losing data. Each one is safe to run again:
```bash
psql -h localhost -p 5432 -U postgres -d leapvengersdb -v ON_ERROR_STOP=1 -f database/migrations/001_limit_and_stop_orders.sql
```

### 3. Install Python market data dependency
```bash
python -m pip install -r scripts/requirements.txt
```

### 4. Backfill one year of Yahoo Finance prices
```bash
$d = { param($days) (Get-Date).AddDays($days).ToString('yyyy-MM-dd') }
$db = '--host localhost --port 5432 --database leapvengersdb --user postgres'.Split(' ')
# Daily closes for the year, 30-minute bars for the last two months, 1-minute bars for the last week
python scripts/yahoo_backfill.py @db --start-date (& $d -365) --end-date (& $d -58) --interval 1d
python scripts/yahoo_backfill.py @db --start-date (& $d -58) --end-date (& $d -6) --interval 30m
python scripts/yahoo_backfill.py @db --start-date (& $d -6) --end-date (& $d 1) --interval 1m
```

### 5. Start live prices (real Yahoo Finance quotes, every 3 seconds while each market is open)
```bash
python scripts/live_quote_generator.py --host localhost --port 5432 --database leapvengersdb --user postgres --interval-seconds 3 --iterations 0
```

Instrument tickers are stored in Yahoo Finance format so the backfill script can query them directly.

Yahoo keeps fine-grained history only for recent dates, so the year is loaded in three ranges that don't overlap: daily, 30-minute and 1-minute bars.

### 6. Verify Setup
```bash
psql -h localhost -p 5432 -U postgres -d leapvengersdb -c "\dt"
```

You should see these tables:
- clients
- users
- accounts
- instruments
- holdings
- orders
- cash_transactions
- price_quotes

To confirm the historical load:

```bash
psql -h localhost -p 5432 -U postgres -d leapvengersdb -c "SELECT instrument_id, COUNT(*), MIN(quote_timestamp), MAX(quote_timestamp) FROM price_quotes GROUP BY instrument_id ORDER BY instrument_id;"
```

Quote history comes from the Yahoo Finance backfill, and live prices from `live_quote_generator.py` (real Yahoo Finance quotes). `--source simulated` is an offline fallback whose prices are labelled `SIMULATED`.

