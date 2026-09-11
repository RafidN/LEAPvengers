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
3. ✓ **Runs Schema** - Executes `Database/enterprise-schema.sql` to set up tables
4. ✓ **Loads Seed Data** - Executes `Database/seed.sql` to populate test data (clients, accounts, instruments, orders, etc.)
5. ✓ **Builds Backend** - Runs `mvn clean install` for the Spring Boot application
6. ✓ **Installs Frontend Dependencies** - Installs npm packages for Angular
7. ✓ **Starts Backend** - Launches Spring Boot on `http://localhost:8081/api` in a new terminal
8. ✓ **Starts Frontend** - Launches Angular dev server on `http://localhost:4200` in a new terminal

## Prerequisites

Before running the script, ensure you have:

- **PostgreSQL 12+** installed and running
  - Default connection: `localhost:5432`
  - User: `postgres` / Password: `n3u3d4!`
- **Java 17+** installed
- **Maven 3.6+** installed and in PATH
- **Node.js 18+** and **npm** installed
- **Git** installed (optional, for version control)

### Windows Execution Policy

If you get an error about script execution policies, run this first:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

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
- **Price Quotes** - Recent market data for all instruments

Use this data to test the dashboard, portfolio views, order history, and other features. The data reflects realistic trading patterns.

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
psql -h localhost -p 5432 -U postgres -d leapvengersdb -f Database/enterprise-schema.sql

# Load seed data (test data for development)
psql -h localhost -p 5432 -U postgres -d leapvengersdb -f Database/seed.sql

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
- Database: `Database/LOCALDB.md`
