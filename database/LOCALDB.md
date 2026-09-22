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

### 3. Install Python market data dependency
```bash
python -m pip install -r scripts/requirements.txt
```

### 4. Backfill one year of Yahoo Finance prices
```bash
$startDate = (Get-Date).AddDays(-365).ToString('yyyy-MM-dd')
$endDate = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
python scripts/yahoo_backfill.py --host localhost --port 5432 --database leapvengersdb --user postgres --start-date $startDate --end-date $endDate --interval auto
```

### 5. Start the 15-second live quote generator
```bash
python scripts/live_quote_generator.py --host localhost --port 5432 --database leapvengersdb --user postgres --interval-seconds 15 --iterations 0
```

Instrument tickers are stored in Yahoo Finance format so the backfill script can query them directly.

Yahoo intraday data is limited, so the backfill script auto-selects the finest interval Yahoo supports for the requested one-year range. In practice, a full year usually resolves to daily bars.

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

This setup now relies on Yahoo Finance backfill for quote history and does not start a local quote simulator.

