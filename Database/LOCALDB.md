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
psql -h localhost -p 5432 -U postgres -d leapvengersdb -f Database/enterprise-schema.sql
```

### 3. Verify Setup
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

