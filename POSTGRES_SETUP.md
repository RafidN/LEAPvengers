# PostgreSQL Docker Setup Guide

This guide explains how to run a PostgreSQL database in Docker for the LEAPvengers project.

## Quick Start

### 1. Start PostgreSQL Container

From the project root directory, run:

```bash
docker-compose up -d postgres
```

The `-d` flag runs the container in the background (detached mode).

### 2. Verify PostgreSQL is Running

```bash
# Check container status
docker-compose ps

# You should see the 'leap-postgres-db' container as "Up"
```

### 3. Connect to the Database

**From your Java application:**
- Host: `postgres` (when running other Docker containers)
- Host: `localhost` (when running from your host machine)
- Port: `5432` (or whatever you set in .env)
- Database: `leapvengersdb`
- Username: `postgres`
- Password: `leapvengers` (change this in .env for production!)

**Using command line (psql):**

```bash
# Connect to the database from localhost
docker-compose exec postgres psql -U postgres -d leapvengersdb

# Connect to the database from another machine (This is mark's linux machine ip)
docker-compose exec postgres psql -h 10.14.130.5 -U postgres -d leapvengersdb
```

**Using a GUI tool:**
- DBeaver (free, feature-rich)
- pgAdmin (web-based)
- DataGrip (JetBrains)

Connection details:
- Server: `localhost`
- Port: `5432`
- Database: `leapvengersdb`
- User: `postgres`
- Password: `leapvengers`

## File Explanations

### `docker-compose.yml`
Defines the PostgreSQL service with:
- **Image**: Official PostgreSQL 15 Alpine (lightweight)
- **Port mapping**: Exposes port 5432 to your machine
- **Volumes**: 
  - `postgres_data`: Persists database files between container restarts
  - `enterprise-schema.sql`: Initialization script that runs on first startup
- **Health checks**: Automatically verifies PostgreSQL is responsive
- **Environment variables**: Loaded from `.env` file for configuration

### `.env`
Configuration file containing:
- `POSTGRES_PASSWORD`: Database password (change this!)
- `POSTGRES_DB`: Database name to create
- `POSTGRES_USER`: Superuser name
- `POSTGRES_PORT`: Port exposed to host machine

**Important:** In production, add `.env` to `.gitignore` and use strong passwords.

### `enterprise-schema.sql`
Initialization script that:
- Creates the `leap` schema for organizing tables
- Creates 7 tables: clients, accounts, instruments, holdings, orders, price_quotes, cash_transactions
- Creates indexes for query performance
- Includes commented sample data for testing
- Sets proper foreign key relationships with cascading deletes

## Common Commands

### Start the database
```bash
docker-compose up -d postgres
```

### Stop the database (preserves data)
```bash
docker-compose down
```

### Stop the database and delete all data
```bash
docker-compose down -v
```
The `-v` flag removes the named volume containing database files.

### View logs
```bash
docker-compose logs -f postgres
```
The `-f` flag follows new log entries (similar to `tail -f`).

### Execute SQL commands
```bash
docker-compose exec postgres psql -U postgres -d leapvengersdb -c "SELECT * FROM leap.clients;"
```

### Backup the database
```bash
docker-compose exec postgres pg_dump -U postgres leapvengersdb > backup.sql
```

### Restore from backup
```bash
docker-compose exec -T postgres psql -U postgres leapvengersdb < backup.sql
```

## Connecting from Your Java Application

### Update application.properties

```properties
# Database connection settings
spring.datasource.url=jdbc:postgresql://postgres:5432/leapvengersdb
spring.datasource.username=postgres
spring.datasource.password=postgres

# Connection pool settings
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

# Dialect for Hibernate (if using JPA/Hibernate)
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQL15Dialect
spring.jpa.hibernate.ddl-auto=validate
```

### If running Java app in Docker too

Update your `docker-compose.yml` to include your Java service:

```yaml
services:
  postgres:
    # ... existing postgres configuration ...
  
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/leapvengersdb
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
    volumes:
      - ./:/app
```

Then start both services:
```bash
docker-compose up -d
```

## Security Considerations

1. **Change the default password**: Update `POSTGRES_PASSWORD` in `.env`
2. **Use `.env` file**: Never commit passwords to git - add `.env` to `.gitignore`
3. **Limit exposed ports**: Only expose necessary ports to your network
4. **Regular backups**: Implement automated backup strategy for production
5. **Use environment-specific configs**: Different passwords for dev/staging/production
6. **Network isolation**: Use Docker networks to isolate containers

## Troubleshooting

### Connection refused
```bash
# Check if container is running
docker-compose ps

# View logs for errors
docker-compose logs postgres
```

### Port already in use
Change the port in `.env`:
```
POSTGRES_PORT=5433
```
Then connect to `localhost:5433` instead.

### Data not persisting
Ensure `postgres_data` volume exists:
```bash
docker volume ls | grep postgres_data
```

### Need to reinitialize database
Delete the volume and restart:
```bash
docker-compose down -v
docker-compose up -d postgres
```

## Performance Tuning

For production databases, modify the Dockerfile or docker-compose.yml:

```yaml
command: 
  - "postgres"
  - "-c"
  - "max_connections=200"
  - "-c"
  - "shared_buffers=256MB"
```

## Additional Resources

- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker PostgreSQL Image](https://hub.docker.com/_/postgres)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)
