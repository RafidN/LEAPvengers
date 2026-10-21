#!/bin/sh
# Market data and database jobs for the platform (scripts/Dockerfile).
#
#   init-db   Create the schema, demo data and a year of prices, but only on an empty database. On a
#             database that already has the platform's tables it only applies database/migrations (each
#             one is safe to run again), so it is safe to run on every deployment. RESET_DATABASE=true
#             wipes and rebuilds instead (all data is lost).
#   feed      Write live prices every few seconds (QUOTE_SOURCE=live, the default, or simulated).
#   backfill  Load Yahoo Finance price history: daily for the year, 30-minute for two months, 1-minute
#             for the last week.
#
# Connection: PGHOST, PGPORT (5432), PGDATABASE (leapvengersdb), PGUSER (postgres), PGPASSWORD, and
# PGSSLMODE (e.g. require, for AWS RDS), read by psql and passed to the Python scripts.
set -eu

: "${PGHOST:?Set PGHOST to the database host}"
: "${PGPASSWORD:?Set PGPASSWORD to the database password}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-leapvengersdb}"
export PGUSER="${PGUSER:-postgres}"

# Runs one of the Python scripts against the database; the password reaches it as PGPASSWORD
run_script() {
    script="$1"
    shift
    python "scripts/$script" --host "$PGHOST" --port "$PGPORT" --database "$PGDATABASE" --user "$PGUSER" "$@"
}

log() {
    echo "[$(date -u '+%Y-%m-%d %H:%M:%S')] $*"
}

wait_for_db() {
    tries=0
    until pg_isready -q; do
        tries=$((tries + 1))
        if [ "$tries" -ge 60 ]; then
            log "Database $PGHOST:$PGPORT/$PGDATABASE is not reachable after 2 minutes"
            exit 1
        fi
        log "Waiting for the database..."
        sleep 2
    done
}

# Daily bars for the year, 30-minute bars for the last two months, 1-minute bars for the last week: the
# finest Yahoo keeps for each, so price charts have the same density of points for every day.
backfill() {
    log "Loading price history from Yahoo Finance"
    run_script yahoo_backfill.py --start-date "$(date -u -d '-365 days' +%F)" --end-date "$(date -u -d '-58 days' +%F)" --interval 1d
    run_script yahoo_backfill.py --start-date "$(date -u -d '-58 days' +%F)" --end-date "$(date -u -d '-6 days' +%F)" --interval 30m
    run_script yahoo_backfill.py --start-date "$(date -u -d '-6 days' +%F)" --end-date "$(date -u -d '+1 day' +%F)" --interval 1m
}

command="${1:-feed}"
case "$command" in
    init-db)
        wait_for_db
        initialised=$(psql -tAc "SELECT to_regclass('public.instruments') IS NOT NULL")
        if [ "$initialised" = "t" ] && [ "${RESET_DATABASE:-false}" != "true" ]; then
            log "Database already initialised; applying migrations (RESET_DATABASE=true rebuilds it, losing all data)"
            for migration in database/migrations/*.sql; do
                log "Applying $migration"
                psql -q -v ON_ERROR_STOP=1 -f "$migration"
            done
            exit 0
        fi
        if [ "$initialised" = "t" ]; then
            log "RESET_DATABASE=true: dropping and rebuilding every table"
        fi
        log "Creating the schema"
        psql -q -v ON_ERROR_STOP=1 -f database/enterprise-schema.sql
        log "Loading demo data"
        psql -q -v ON_ERROR_STOP=1 -f database/seed.sql
        # Price history is nice to have, not essential: the live feed prices everything from its first minute.
        # If Yahoo refuses (e.g. rate limiting), finish set-up anyway and say how to load it later.
        if ! backfill; then
            log "WARNING: price history did not load completely; charts will be thin until the 'backfill' job is run again"
        fi
        log "Database ready"
        ;;
    feed)
        wait_for_db
        log "Starting the ${QUOTE_SOURCE:-live} price feed"
        set -- --source "${QUOTE_SOURCE:-live}" --interval-seconds "${QUOTE_INTERVAL_SECONDS:-3}"
        if [ "${QUOTE_ALL_HOURS:-false}" = "true" ]; then
            set -- "$@" --all-hours
        fi
        exec python scripts/live_quote_generator.py --host "$PGHOST" --port "$PGPORT" --database "$PGDATABASE" \
            --user "$PGUSER" "$@"
        ;;
    backfill)
        wait_for_db
        backfill
        ;;
    *)
        exec "$@"
        ;;
esac
