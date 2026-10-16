import argparse
import csv
import importlib
import io
import math
import os
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone


@dataclass
class InstrumentMapping:
    instrument_id: int
    ticker: str


INTRADAY_INTERVALS = {"1m", "2m", "5m", "15m", "30m", "60m", "90m", "1h"}

# The platform holds and trades everything in US dollars, so prices quoted in another currency (e.g.
# NSE stocks in rupees) are converted on the way in. Yahoo quotes some markets in a minor unit; those
# are scaled to the major currency first (e.g. London stocks in pence -> pounds).
MINOR_UNITS = {"GBp": ("GBP", 100), "GBX": ("GBP", 100), "ILA": ("ILS", 100), "ZAc": ("ZAR", 100)}
MAX_INTRADAY_LOOKBACK_DAYS = 60


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Backfill historical Yahoo Finance quotes into price_quotes."
    )
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default="5432")
    parser.add_argument("--database", default="leapvengersdb")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--password", default=os.environ.get("PGPASSWORD", ""))
    parser.add_argument("--start-date", default=None)
    parser.add_argument("--end-date", default=None)
    parser.add_argument("--interval", default="auto")
    parser.add_argument("--batch-size", type=int, default=500)
    return parser.parse_args()


def resolve_date_range(args: argparse.Namespace) -> tuple[str, str]:
    today = datetime.now(timezone.utc).date()
    default_end = today + timedelta(days=1)

    default_start = today - timedelta(days=365)

    start_value = args.start_date or default_start.isoformat()
    end_value = args.end_date or default_end.isoformat()
    return start_value, end_value


def select_interval(start_date: str, end_date: str, requested_interval: str) -> str:
    if requested_interval != "auto":
        return requested_interval

    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")
    span = end - start

    if span <= timedelta(days=7):
        return "1m"
    if span <= timedelta(days=60):
        return "30m"
    return "1d"


def validate_date_range(start_date: str, end_date: str, interval: str) -> None:
    start = datetime.strptime(start_date, "%Y-%m-%d")
    end = datetime.strptime(end_date, "%Y-%m-%d")
    if end <= start:
        raise RuntimeError("end-date must be after start-date.")

    if interval not in INTRADAY_INTERVALS:
        return

    now = datetime.now(timezone.utc)
    intraday_cutoff = now - timedelta(days=MAX_INTRADAY_LOOKBACK_DAYS)
    if start < intraday_cutoff.replace(tzinfo=None) or (end - start) > timedelta(days=MAX_INTRADAY_LOOKBACK_DAYS):
        raise RuntimeError(
            f"Yahoo Finance intraday interval '{interval}' is limited to roughly the most recent {MAX_INTRADAY_LOOKBACK_DAYS} days. "
            f"Requested range: {start_date} to {end_date}. Use a recent window or switch to 1d for older history."
        )


def run_psql(args: argparse.Namespace, sql: str, capture_output: bool = True) -> str:
    env = os.environ.copy()
    if args.password:
        env["PGPASSWORD"] = args.password

    command = [
        "psql",
        "-h",
        args.host,
        "-p",
        str(args.port),
        "-U",
        args.user,
        "-d",
        args.database,
        "-X",
        "-v",
        "ON_ERROR_STOP=1",
        "-c",
        sql,
    ]

    if capture_output:
        completed = subprocess.run(
            command,
            env=env,
            check=True,
            capture_output=True,
            text=True,
        )
        return completed.stdout

    subprocess.run(command, env=env, check=True)
    return ""


def sql_escape(value: str) -> str:
    return value.replace("'", "''")


def fetch_instruments(args: argparse.Namespace) -> list[InstrumentMapping]:
    sql = """
    COPY (
        SELECT instrument_id, ticker
        FROM instruments
        ORDER BY instrument_id
    ) TO STDOUT WITH CSV
    """
    output = run_psql(args, sql)
    reader = csv.reader(io.StringIO(output))
    instruments = []
    for row in reader:
        if not row:
            continue
        instruments.append(
            InstrumentMapping(
                instrument_id=int(row[0]),
                ticker=row[1],
            )
        )
    return instruments


def get_yfinance_module():
    try:
        return importlib.import_module("yfinance")
    except ModuleNotFoundError as error:
        raise RuntimeError(
            "yfinance is not installed. Run 'python -m pip install -r scripts/requirements.txt'."
        ) from error


def normalize_timestamp(index_value) -> str:
    timestamp = index_value.to_pydatetime() if hasattr(index_value, "to_pydatetime") else index_value
    if timestamp.tzinfo is not None:
        timestamp = timestamp.astimezone().replace(tzinfo=None)
    return timestamp.strftime("%Y-%m-%d %H:%M:%S")


def load_usd_rates(currency: str, args: argparse.Namespace, cache: dict) -> list[tuple]:
    """
    (date, units of currency per US dollar) for the backfill window, oldest first, from Yahoo's
    "<CCY>=X" series (e.g. INR=X is rupees per dollar). Read once per currency.
    """
    if currency in cache:
        return cache[currency]
    yfinance = get_yfinance_module()
    history = yfinance.Ticker(f"{currency}=X").history(
        start=args.start_date,
        end=args.end_date,
        interval="1d",
        auto_adjust=False,
        actions=False,
        raise_errors=False,
    )
    rates = [
        (index_value.date(), float(quote["Close"]))
        for index_value, quote in history.iterrows()
        if quote.get("Close") is not None and not math.isnan(float(quote["Close"])) and float(quote["Close"]) > 0
    ]
    if not rates:
        raise RuntimeError(f"No {currency}=X exchange rates from Yahoo; cannot convert {currency} prices to USD.")
    cache[currency] = rates
    return rates


def rate_on(rates: list[tuple], day) -> float:
    """The rate on that day, else the last one before it (weekends, holidays), else the first one after."""
    chosen = rates[0][1]
    for rate_day, rate in rates:
        if rate_day > day:
            break
        chosen = rate
    return chosen


def load_history_rows(instrument: InstrumentMapping, args: argparse.Namespace,
                      rate_cache: dict | None = None) -> list[tuple[int, float, int | None, str]]:
    yfinance = get_yfinance_module()
    yahoo_ticker = yfinance.Ticker(instrument.ticker)
    history = yahoo_ticker.history(
        start=args.start_date,
        end=args.end_date,
        interval=args.interval,
        auto_adjust=False,
        actions=False,
        raise_errors=False,
    )

    if history.empty:
        print(
            f"Skipping {instrument.ticker}: no Yahoo data returned.",
            file=sys.stderr,
            flush=True,
        )
        return []

    currency = (getattr(yahoo_ticker, "history_metadata", None) or {}).get("currency") or "USD"
    currency, minor_unit_divisor = MINOR_UNITS.get(currency, (currency, 1))
    rates = None
    if currency != "USD":
        rates = load_usd_rates(currency, args, rate_cache if rate_cache is not None else {})
        print(f"Converting {instrument.ticker} from {currency} to USD at each day's {currency}=X rate", flush=True)

    rows = []
    for index_value, quote in history.iterrows():
        close_price = quote.get("Close")
        if close_price is None or math.isnan(float(close_price)):
            continue
        close_price = float(close_price) / minor_unit_divisor
        if rates is not None:
            day = (index_value.to_pydatetime() if hasattr(index_value, "to_pydatetime") else index_value).date()
            close_price = close_price / rate_on(rates, day)

        raw_volume = quote.get("Volume")
        volume = None
        if raw_volume is not None and not math.isnan(float(raw_volume)):
            volume = int(raw_volume)

        rows.append(
            (
                instrument.instrument_id,
                round(float(close_price), 4),
                volume,
                normalize_timestamp(index_value),
            )
        )

    return rows


def build_insert_sql(rows: list[tuple[int, float, int | None, str]]) -> str:
    values = []
    for instrument_id, price, volume, quote_timestamp in rows:
        volume_sql = "NULL" if volume is None else str(volume)
        values.append(
            f"({instrument_id}, {price:.4f}, {volume_sql}, TIMESTAMP '{sql_escape(quote_timestamp)}')"
        )

    return """
    INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp)
    VALUES
    {values}
    ON CONFLICT (instrument_id, quote_timestamp) DO NOTHING;
    """.format(values=",\n".join(values))


def insert_rows(args: argparse.Namespace, rows: list[tuple[int, float, int | None, str]]) -> None:
    if not rows:
        return

    batch_size = max(args.batch_size, 1)
    for index in range(0, len(rows), batch_size):
        batch = rows[index:index + batch_size]
        run_psql(args, build_insert_sql(batch), capture_output=False)


def refresh_views(args: argparse.Namespace) -> None:
    sql = """
    REFRESH MATERIALIZED VIEW latest_price_quotes;
    REFRESH MATERIALIZED VIEW account_valuations;
    """
    run_psql(args, sql, capture_output=False)


def main() -> int:
    args = parse_args()
    args.start_date, args.end_date = resolve_date_range(args)
    args.interval = select_interval(args.start_date, args.end_date, args.interval)
    validate_date_range(args.start_date, args.end_date, args.interval)
    print(
        f"Backfilling Yahoo Finance quotes from {args.start_date} to {args.end_date} ({args.interval})",
        flush=True,
    )

    try:
        instruments = fetch_instruments(args)
        if not instruments:
            print("No instruments found; aborting backfill.", file=sys.stderr, flush=True)
            return 1

        total_insert_candidates = 0
        rate_cache: dict = {}
        for instrument in instruments:
            rows = load_history_rows(instrument, args, rate_cache)
            insert_rows(args, rows)
            total_insert_candidates += len(rows)
            print(
                f"Loaded {len(rows)} rows for {instrument.ticker}",
                flush=True,
            )

        refresh_views(args)
        print(
            f"Historical backfill complete. Prepared {total_insert_candidates} quote rows.",
            flush=True,
        )
        return 0
    except subprocess.CalledProcessError as error:
        if error.stderr:
            print(error.stderr.strip(), file=sys.stderr, flush=True)
        else:
            print(str(error), file=sys.stderr, flush=True)
        return error.returncode or 1
    except Exception as error:
        print(str(error), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())