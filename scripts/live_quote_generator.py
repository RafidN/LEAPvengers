import argparse
import csv
import io
import math
import os
import random
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, time as dt_time

import pytz
import pytz



FX_TICKERS = {"EURUSD=X", "GBPUSD=X", "USDJPY=X"}
CRYPTO_TICKERS = {"BTC-USD", "ETH-USD", "SOL-USD"}

ASSET_PROFILES = {
    "Equity": {"drift": 0.0003, "volatility": 0.004, "volume_jitter": 0.08},
    "Bond": {"drift": 0.0001, "volatility": 0.0015, "volume_jitter": 0.05},
    "Fund": {"drift": 0.0002, "volatility": 0.0025, "volume_jitter": 0.05},
    "Cash": {"drift": 0.0, "volatility": 0.001, "volume_jitter": 0.03},
}


@dataclass
class InstrumentQuote:
    instrument_id: int
    ticker: str
    asset_class: str
    price: float
    volume: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Append simulated live quotes every few seconds using the latest stored quote as the base."
    )
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default="5432")
    parser.add_argument("--database", default="leapvengersdb")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--password", default=os.environ.get("PGPASSWORD", ""))
    parser.add_argument("--interval-seconds", type=int, default=15)
    parser.add_argument("--iterations", type=int, default=0)
    parser.add_argument("--skip-trading-hours", action="store_true", help="Generate quotes 24/7 (for testing)")
    return parser.parse_args()


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


def fetch_instruments(args: argparse.Namespace) -> list[InstrumentQuote]:
    sql = """
    COPY (
        SELECT
            i.instrument_id,
            i.ticker,
            i.asset_class,
            COALESCE(lpq.price, 100.0) AS price,
            COALESCE(lpq.volume, 1000000) AS volume
        FROM instruments i
        LEFT JOIN LATERAL (
            SELECT price, volume
            FROM price_quotes pq
            WHERE pq.instrument_id = i.instrument_id
            ORDER BY pq.quote_timestamp DESC
            LIMIT 1
        ) lpq ON TRUE
        ORDER BY i.instrument_id
    ) TO STDOUT WITH CSV
    """
    output = run_psql(args, sql)
    reader = csv.reader(io.StringIO(output))
    instruments = []
    for row in reader:
        if not row:
            continue
        instruments.append(
            InstrumentQuote(
                instrument_id=int(row[0]),
                ticker=row[1],
                asset_class=row[2],
                price=float(row[3]),
                volume=int(row[4]) if row[4] else 0,
            )
        )
    return instruments


def simulate_quote(instrument: InstrumentQuote) -> tuple[float, int]:
    profile = ASSET_PROFILES.get(instrument.asset_class, ASSET_PROFILES["Equity"])
    drift = profile["drift"]
    volatility = profile["volatility"]
    volume_jitter = profile["volume_jitter"]

    if instrument.ticker in FX_TICKERS:
        drift = 0.0
        volatility = 0.00025
        volume_jitter = 0.015
    elif instrument.ticker in CRYPTO_TICKERS:
        drift = 0.0004
        volatility = 0.0025
        volume_jitter = 0.04

    move = random.gauss(drift, volatility)
    next_price = max(instrument.price * (1 + move), 0.0001)
    next_volume = max(int(instrument.volume * (1 + random.gauss(0, volume_jitter))), 1)
    return round(next_price, 4), next_volume


def is_trading_hours() -> bool:
    """
    Check if current time is within US market trading hours.
    Trading hours: 9:30 AM - 4:00 PM ET, Monday-Friday.
    """
    et_tz = pytz.timezone('US/Eastern')
    now_et = datetime.now(et_tz)
    
    # Check if weekday (0=Monday, 6=Sunday)
    if now_et.weekday() >= 5:  # Saturday or Sunday
        return False
    
    # Check if within trading hours (9:30 AM - 4:00 PM)
    market_open = dt_time(9, 30)
    market_close = dt_time(16, 0)
    current_time = now_et.time()
    
    return market_open <= current_time < market_close


def build_insert_sql(instruments: list[InstrumentQuote], quote_timestamp: str) -> str:
    values = []
    for instrument in instruments:
        price, volume = simulate_quote(instrument)
        values.append(
            f"({instrument.instrument_id}, {price:.4f}, {volume}, TIMESTAMP '{quote_timestamp}')"
        )

    return """
    INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp)
    VALUES
    {values};

    REFRESH MATERIALIZED VIEW latest_price_quotes;
    REFRESH MATERIALIZED VIEW account_valuations;
    """.format(values=",\n".join(values))


def main() -> int:
    args = parse_args()
    interval_seconds = max(args.interval_seconds, 1)
    iteration_limit = args.iterations if args.iterations > 0 else None

    print(
        f"Starting live quote generator for {args.database} on {args.host}:{args.port} every {interval_seconds}s",
        flush=True,
    )

    try:
        iteration = 0
        while True:
            should_generate = args.skip_trading_hours or is_trading_hours()
            if should_generate:
                instruments = fetch_instruments(args)
                if not instruments:
                    print("No instruments found; stopping generator.", file=sys.stderr, flush=True)
                    return 1

                quote_timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                run_psql(args, build_insert_sql(instruments, quote_timestamp), capture_output=False)
                iteration += 1
                print(
                    f"[{quote_timestamp}] inserted {len(instruments)} simulated live quotes (iteration {iteration})",
                    flush=True,
                )

                if iteration_limit is not None and iteration >= iteration_limit:
                    return 0
            else:
                # Outside trading hours; log and wait
                quote_timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                print(
                    f"[{quote_timestamp}] outside trading hours (9:30 AM - 4:00 PM ET, Mon-Fri); sleeping...",
                    flush=True,
                )

            time.sleep(interval_seconds)
    except KeyboardInterrupt:
        print("Live quote generator stopped.", flush=True)
        return 0
    except subprocess.CalledProcessError as error:
        if error.stderr:
            print(error.stderr.strip(), file=sys.stderr, flush=True)
        else:
            print(str(error), file=sys.stderr, flush=True)
        return error.returncode or 1


if __name__ == "__main__":
    raise SystemExit(main())