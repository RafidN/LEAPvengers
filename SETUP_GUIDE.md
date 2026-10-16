# LEAPvengers Local Development Setup Guide

## Quick Start

Run this script from the project root directory:

```powershell
.\setup-dev.ps1
```

## What the Script Does

The `setup-dev.ps1` script automates your entire local development setup:

1. ✓ **Checks PostgreSQL Connection** - Verifies PostgreSQL is running and accessible
2. ✓ **Creates Database** - Creates `leapvengersdb` if it doesn't exist
3. ✓ **Runs Schema** - Executes `database/enterprise-schema.sql` to set up tables
4. ✓ **Loads Seed Data** - Executes `database/seed.sql` to populate test data (clients, accounts, instruments, orders, etc.)
5. ✓ **Installs Python Market Data Dependencies** - Installs `yfinance` for historical market-data backfill
6. ✓ **Backfills One Year Of Prices** - Loads one year of Yahoo Finance history at the finest interval Yahoo supports for that date range
7. ✓ **Starts Live Quote Generator** - Streams real Yahoo Finance prices for every open market and writes them every 3 seconds (see [Market data and trading hours](#market-data-and-trading-hours))
8. ✓ **Builds Backend** - Runs `mvn clean install` for the Spring Boot application
9. ✓ **Installs Frontend Dependencies** - Installs npm packages for Angular
10. ✓ **Starts Backend** - Launches Spring Boot on `http://localhost:8081/api` in a new terminal
11. ✓ **Starts Frontend** - Launches Angular dev server on `http://localhost:4200` in a new terminal

## Prerequisites

Before running the script, ensure you have:

- **PostgreSQL 12+** installed and running
  - Default connection: `localhost:5432`
  - User: `postgres` / Password: `n3u3d4!`
- **Java 17+** installed
- **Maven 3.6+** installed and in PATH
- **Node.js 18+** and **npm** installed
- **Python 3.10+** installed and in PATH
- **Git** installed (optional, for version control)

### Windows Execution Policy

If you get an error about script execution policies, run this first:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## Docker Option (Automatic)

For Docker-based services (PostgreSQL + SonarQube), use the included scripts:

**Windows:** `.\start-services.ps1`  
**macOS/Linux:** `./start-services.sh`

Jenkins CI/CD automatically uses Docker Compose for all stages.

## After Setup

Once the script completes, you'll see output similar to:

```
Backend (Spring Boot API):
  URL: http://localhost:8081/api
  PID: 12345

Frontend (Angular):
  URL: http://localhost:4200
  PID: 12346

Database:
  Host: localhost
  Port: 5432
  Database: leapvengersdb
```

Open your browser and navigate to:
- **Frontend**: http://localhost:4200
- **API Documentation**: http://localhost:8081/api (if available)

## Seed Data

The setup automatically loads test data that includes:
- **5 Test Clients** - Ready for authentication testing
- **12 Instruments** - Stocks (AAPL, GOOGL, MSFT, etc.), ETFs, bonds, and cash equivalents
- **6 Accounts** - With various cash balances ($25K–$100K)
- **Test Users** - Different usernames and hashed passwords for each client
- **Historical Orders** - Mix of BUY/SELL orders with different statuses
- **Holdings** - Portfolio positions based on filled orders
- **Price Quotes** - One year of Yahoo Finance history plus live Yahoo Finance prices every 3 seconds while each market is open

Instrument tickers are seeded in Yahoo Finance format so the backfill script uses the same symbols.

Because Yahoo intraday history is limited, the backfill script automatically falls back to the finest interval Yahoo supports for the requested one-year range. For a full year, that typically means daily bars.

Use this data to test the dashboard, portfolio views, order history, and other features. The data reflects realistic trading patterns.

### Demo personas

The sign-in page has one-click buttons for the three personas from the business requirements. Every seeded password is `Test123`.

| Username | Role | Lands on | What to show |
|----------|------|----------|--------------|
| `joanna` | Trader | `/dashboard` | Cash, holdings across US/UK/Indian stocks, crypto and FX; order history with filled, pending, rejected and canceled orders |
| `david` | Ops | `/clients` | Every client with their segment; trade lifecycles |
| `priya` | Analyst | `/insights` | Activity and value by segment, clients shown by id only |

Every role also has `/markets` and `/access`. The Access page calls the real API and shows which requests the server blocks for the signed-in role.

## Stopping Services

The script opens new PowerShell windows for backend and frontend. To stop them:

1. Close the terminal windows, OR
2. Run the commands shown at the end of the script output

## Troubleshooting

### PostgreSQL Not Running
- **Windows**: Start PostgreSQL from Services (services.msc) or PostgreSQL installation folder
- Check connection: `psql -h localhost -p 5432 -U postgres`

### Maven Build Fails
- Run manually for detailed errors: `cd backend && mvn clean install`
- Check Java version: `java -version` (should be 17+)

### npm Install Fails
- Clear cache: `npm cache clean --force`
- Delete node_modules: `rm -r frontend/node_modules` then run script again

### Port Already in Use
- Backend (8081): Check `netstat -ano | findstr :8081`
- Frontend (4200): Check `netstat -ano | findstr :4200`
- Kill process: `taskkill /PID <PID> /F`

### Permission Denied Errors
- Run PowerShell as Administrator
- Or adjust execution policy: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

## Manual Alternative

If the script doesn't work for your setup, run these commands manually:

```powershell
# Create database
psql -h localhost -p 5432 -U postgres -c "CREATE DATABASE leapvengersdb;"

# Run schema
psql -h localhost -p 5432 -U postgres -d leapvengersdb -f database/enterprise-schema.sql

# Load seed data (test data for development)
psql -h localhost -p 5432 -U postgres -d leapvengersdb -f database/seed.sql

# Install Python dependency and backfill one year of Yahoo prices
python -m pip install -r scripts/requirements.txt
$startDate = (Get-Date).AddDays(-365).ToString('yyyy-MM-dd')
$endDate = (Get-Date).AddDays(1).ToString('yyyy-MM-dd')
python scripts/yahoo_backfill.py --host localhost --port 5432 --database leapvengersdb --user postgres --start-date $startDate --end-date $endDate --interval auto

# Start live prices (real Yahoo Finance quotes; orders are rejected without a current price)
python scripts/live_quote_generator.py --host localhost --port 5432 --database leapvengersdb --user postgres --interval-seconds 3 --iterations 0

# Build backend
cd backend
mvn clean install
cd ..

# Install frontend dependencies
cd frontend
npm install
cd ..

# Terminal 1: Start backend
cd backend
mvn spring-boot:run

# Terminal 2: Start frontend
cd frontend
npm start
```

## Environment Configuration

Key configuration files:

- **Backend**: `backend/src/main/resources/application.properties`
  - Database connection
  - JWT settings
  - Server port (8081)

- **Frontend**: `frontend/angular.json` and `frontend/proxy.conf.json`
  - Angular build configuration
  - API proxy settings

## Support

If you encounter issues, check:
1. The detailed error message in the script output
2. Prerequisites are installed correctly
3. PostgreSQL is running and accessible
4. No ports are already in use (8081, 4200)
5. Java version is 17+

For more details, see individual README files:
- Backend: `backend/README.md`
- Frontend: `frontend/README.md`
- Database: `database/LOCALDB.md`

## Market data and trading hours

Orders are priced on current market quotes (BR-08, BR-12) and only accepted while the instrument can be traded (BR-05):

- **Live prices.** `scripts/live_quote_generator.py` streams real trade prices from Yahoo Finance and writes each instrument's latest one every 3 seconds, converted to US dollars at the live exchange rate. Every quote row records where it came from (`price_quotes.source`), and every order step records the quote it was priced against (`order_events.quote_price`, `quote_time`, `quote_source`).
- **Delayed exchanges.** Yahoo's free feed is real time for US stocks, FX and crypto, but 15 minutes behind for London (`.L`) and NSE (`.NS`). Those instruments have `quote_delay_minutes = 15`; the ticket and the audit trail say the price is delayed, and the "current price" limit allows the extra 15 minutes.
- **Market hours.** US stocks trade 09:30-16:00 New York time, London 08:00-16:30 London time, NSE 09:15-15:30 India time, FX from Sunday 17:00 to Friday 17:00 New York time, and crypto always. Outside those hours the order ticket explains when the market opens and orders are rejected. Exchange holidays aren't listed; on a holiday no prices arrive, so orders are rejected because the latest price is too old.
- **Current price.** An order is rejected if the latest quote is older than `leap.trading.max-price-age` (1 minute, plus any exchange delay). If the generator isn't running, every order is rejected for this reason.
- **Suspending an instrument.** The trading desk can stop trading in one instrument: `UPDATE instruments SET tradable = false WHERE ticker = 'NFLX';`
- **Demo outside market hours.** Run the generator with `--source simulated --all-hours` and start the backend with `--leap.trading.enforce-market-hours=false`. Simulated prices are labelled `SIMULATED` everywhere, so they're never mistaken for market data.

## Trade records

- **Permanent.** Database triggers stop orders and cash movements from being edited or deleted. An order can only move forward (SUBMITTED → ACCEPTED or REJECTED → FILLED or FAILED), and order events are append-only. Corrections are new records. The triggers stop the application and anyone using its database login; in production, the application should also connect as a role without rights to drop the triggers.
- **Restart recovery.** If the backend stops during an order's half-second fill delay, `OrderRecoveryService` finishes the order when the backend next starts. It also checks every 10 seconds after that. The order is filled if its price still holds, or marked FAILED with "The platform restarted before this order was filled…". An order left mid-check is REJECTED, and nothing moves.

