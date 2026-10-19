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
6. ✓ **Backfills One Year Of Prices** - Loads Yahoo Finance history: daily closes for the year, 30-minute bars for the last two months and 1-minute bars for the last week
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

## Docker Option (the whole platform in containers)

With Docker Desktop installed you don't need Java, Node, Python or PostgreSQL on your machine. The same images run on AWS.

```powershell
Copy-Item .env.example .env    # optional: every setting has a local default
docker compose up --build -d   # or: .\start-services.ps1
```

Then open http://localhost:8080 and sign in as `joanna`, `david` or `priya` (password `Test123`). The API is also on http://localhost:8081/api.

The first start takes a few minutes: it builds the images, then the `db-init` job creates the schema, demo data and a year of prices before the backend and the live price feed start. Later starts leave the database as it is.

| Command | What it does |
| --- | --- |
| `docker compose logs -f backend` | Follow one service (`backend`, `frontend`, `feed`, `db-init`, `leapdb`) |
| `docker compose down` | Stop; the database is kept |
| `docker compose down -v` | Stop and delete the database (the next start rebuilds it) |

If PostgreSQL is already running on this machine, set `DB_PORT=5433` in `.env` so the two don't clash.

To deploy to AWS, see [deploy/aws/README.md](deploy/aws/README.md). Jenkins (the `Jenkinsfile`) tests every build, builds these images, and can push and deploy them.

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

Yahoo keeps fine-grained history only for recent dates, so setup loads three ranges that don't overlap: daily bars for the year, 30-minute bars for the last two months and 1-minute bars for the last week. Charts sample one price per interval (`GET /api/history/prices`), so every day in a chart has the same density of points.

Use this data to test the dashboard, portfolio views, order history, and other features. The data reflects realistic trading patterns.

### Demo personas

The sign-in page has one-click buttons for the three personas from the business requirements. Every seeded password is `Test123`.

| Username | Role | Lands on | What to show |
|----------|------|----------|--------------|
| `joanna` | Trader | `/dashboard` | Cash, holdings across US/UK/Indian stocks, crypto and FX; order history with filled, submitted, rejected and canceled orders |
| `david` | Ops | `/clients` | Every client with their segment; trade lifecycles |
| `priya` | Analyst | `/insights` | Activity and value by segment, clients shown by id only |

Every role also has `/markets`. Each role's access rules are enforced by the server and covered by the backend tests (`RoleAccessTest`, `AuditControllerSecurityTest` and the integration tests).

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

# Install Python dependency and backfill a year of Yahoo prices
python -m pip install -r scripts/requirements.txt
$d = { param($days) (Get-Date).AddDays($days).ToString('yyyy-MM-dd') }
$db = '--host localhost --port 5432 --database leapvengersdb --user postgres'.Split(' ')
# Daily closes for the year, 30-minute bars for the last two months, 1-minute bars for the last week
python scripts/yahoo_backfill.py @db --start-date (& $d -365) --end-date (& $d -58) --interval 1d
python scripts/yahoo_backfill.py @db --start-date (& $d -58) --end-date (& $d -6) --interval 30m
python scripts/yahoo_backfill.py @db --start-date (& $d -6) --end-date (& $d 1) --interval 1m

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
  - Database connection and JWT secret, from the environment variables `DB_URL`, `DB_USER`, `DB_PASSWORD` and `JWT_SECRET`; the defaults after `:` are for local development only
  - Server port (8081)
  - `application-prod.properties` (the `prod` profile, used by the container image) has no defaults: the backend refuses to start without those variables. It also turns off the Swagger API docs.
- **Docker / AWS**: `.env.example` lists the settings for `docker compose`; on AWS they come from Secrets Manager and the CloudFormation stacks (`deploy/aws/`)

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

- **Permanent.** Database triggers stop orders and cash movements from being edited or deleted. An order can only move forward (SUBMITTED → ACCEPTED or REJECTED → FILLED or FAILED; a limit or stop order goes SUBMITTED → WORKING → ACCEPTED or CANCELED first), and order events are append-only. Corrections are new records. The triggers stop the application and anyone using its database login; in production, the application should also connect as a role without rights to drop the triggers.
- **Disputes.** A client can report a problem with any finished order from History (the order's timeline). It appears in Operations' "Open disputes" queue on the Clients page within 30 seconds; Review opens the order's full record, and Ops resolves it with a written reply the client sees on their History page. Disputes are permanent records: they can only move from open to resolved, once. The seed includes one open dispute (Joanna's failed SAP order).
- **Limit and stop orders.** Besides market orders, a trader can place a limit order (buy at or below a price, or sell at or above one) or a stop order (a stop-loss sells once the price falls to a level; a stop buy buys once it rises to one), from the order ticket or by dragging lines on the Trade page's chart. They wait as WORKING, even while the market is closed, and `WorkingOrderService` checks them against the latest prices every second (`leap.trading.working-orders-interval`). Once the price reaches one and its market is open, it is filled through the same fill as a market order: a limit order at its price or better, a stop order at whatever the price then is. Working buys hold their cash, so it can't be spent twice. Moving an order to a new price cancels it and places a new one, so the original stays on record. When a sell leaves too few shares for another working sell of the same instrument (a stop-loss and take-profit pair), that one is canceled automatically.
- **Restart recovery.** If the backend stops during an order's half-second fill delay, `OrderRecoveryService` finishes the order when the backend next starts. It also checks every 10 seconds after that. The order is filled if its price still holds, or marked FAILED with "The platform restarted before this order was filled…". An order left mid-check is REJECTED, and nothing moves.

