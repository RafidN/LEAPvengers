"""
Writes current market quotes into price_quotes so the platform can price orders (BR-08, BR-12).

--source live (the default) streams real trade prices from Yahoo Finance's live feed and writes the latest
price of each instrument every --interval-seconds, converted to US dollars at the live exchange rate.
Every row is a real price with the provider's own timestamp, labelled source = 'YAHOO_LIVE'. A market only
produces prices while it is open, so equities stop updating when their exchange closes, FX at the weekend,
and crypto never. Yahoo's free data is real time for US stocks, FX and crypto, and 15 minutes behind for
London and NSE (instruments.quote_delay_minutes).

--source simulated is for working offline: a random walk from the latest stored price, labelled
source = 'SIMULATED' so no one mistakes it for a market price. It follows the same market hours unless
--all-hours is given (pair that with leap.trading.enforce-market-hours=false for a demo out of hours).
"""
import argparse
import csv
import io
import math
import os
import random
import subprocess
import sys
import threading
import time
import warnings
from dataclasses import dataclass
from datetime import datetime, time as dt_time, timedelta, timezone

import pytz

# yfinance's streaming client trips a websockets deprecation warning on every connect; it is harmless
warnings.filterwarnings("ignore", category=DeprecationWarning)

FX_TICKERS = {"EURUSD=X", "GBPUSD=X", "AUDUSD=X"}
CRYPTO_TICKERS = {"BTC-USD", "ETH-USD", "SOL-USD"}

# Yahoo quotes some markets in a minor unit; those are scaled to the major currency first (pence -> pounds)
MINOR_UNITS = {"GBp": ("GBP", 100), "GBX": ("GBP", 100), "ILA": ("ILS", 100), "ZAc": ("ZAR", 100)}

# Each exchange's regular weekday session, in its own time zone. Kept in step with MarketHours.java.
SESSIONS = {
    "US": (pytz.timezone("America/New_York"), dt_time(9, 30), dt_time(16, 0)),
    "UK": (pytz.timezone("Europe/London"), dt_time(8, 0), dt_time(16, 30)),
    "IN": (pytz.timezone("Asia/Kolkata"), dt_time(9, 15), dt_time(15, 30)),
}
NEW_YORK = pytz.timezone("America/New_York")
FX_ROLLOVER = dt_time(17, 0)

# Live mode: if the stream sends nothing for this long while a market is open, poll Yahoo's chart API instead
STREAM_SILENCE_SECONDS = 30
POLL_SECONDS = 15

# Simulated mode. Drift and volatility are per tick of BASE_INTERVAL_SECONDS; other intervals are scaled to
# match (drift by time, volatility by the square root of time), so prices move the same amount per minute
# however often quotes are written.
BASE_INTERVAL_SECONDS = 15
ASSET_PROFILES = {
    "Equity": {"drift": 0.0003, "volatility": 0.004, "volume_jitter": 0.08},
    "Bond": {"drift": 0.0001, "volatility": 0.0015, "volume_jitter": 0.05},
    "Fund": {"drift": 0.0002, "volatility": 0.0025, "volume_jitter": 0.05},
    "Cash": {"drift": 0.0, "volatility": 0.001, "volume_jitter": 0.03},
}


@dataclass
class Instrument:
    instrument_id: int
    ticker: str
    asset_class: str
    market: str
    price: float | None
    volume: int
    latest_quote_at: datetime | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default="5432")
    parser.add_argument("--database", default="leapvengersdb")
    parser.add_argument("--user", default="postgres")
    parser.add_argument("--password", default=os.environ.get("PGPASSWORD", ""))
    parser.add_argument("--source", choices=["live", "simulated"], default="live",
                        help="live: real Yahoo Finance prices (default). simulated: an offline random walk.")
    parser.add_argument("--interval-seconds", type=int, default=3, help="How often new prices are written")
    parser.add_argument("--iterations", type=int, default=0, help="Stop after this many writes (0 = run forever)")
    parser.add_argument("--all-hours", "--skip-trading-hours", dest="all_hours", action="store_true",
                        help="Simulated mode only: move prices even while their market is closed")
    return parser.parse_args()


# ---------------------------------------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------------------------------------

def run_psql(args: argparse.Namespace, sql: str, capture_output: bool = True) -> str:
    env = os.environ.copy()
    if args.password:
        env["PGPASSWORD"] = args.password
    command = ["psql", "-h", args.host, "-p", str(args.port), "-U", args.user, "-d", args.database,
               "-X", "-q", "-v", "ON_ERROR_STOP=1", "-c", sql]
    completed = subprocess.run(command, env=env, check=True, capture_output=capture_output, text=True)
    return completed.stdout if capture_output else ""


def fetch_instruments(args: argparse.Namespace) -> list[Instrument]:
    sql = """
    COPY (
        SELECT i.instrument_id, i.ticker, i.asset_class, i.market, lpq.price, COALESCE(lpq.volume, 1000000),
               lpq.quote_timestamp
        FROM instruments i
        LEFT JOIN LATERAL (
            SELECT price, volume, quote_timestamp
            FROM price_quotes pq
            WHERE pq.instrument_id = i.instrument_id
            ORDER BY pq.quote_timestamp DESC
            LIMIT 1
        ) lpq ON TRUE
        ORDER BY i.instrument_id
    ) TO STDOUT WITH CSV
    """
    instruments = []
    for row in csv.reader(io.StringIO(run_psql(args, sql))):
        if not row:
            continue
        instruments.append(Instrument(
            instrument_id=int(row[0]),
            ticker=row[1],
            asset_class=row[2],
            market=row[3],
            price=float(row[4]) if row[4] else None,
            volume=int(row[5]) if row[5] else 0,
            latest_quote_at=datetime.fromisoformat(row[6]) if row[6] else None,
        ))
    return instruments


def write_quotes(args: argparse.Namespace, rows: list[tuple[int, float, int | None, datetime]], source: str) -> None:
    """Inserts (instrument_id, price, volume, quote time) rows and refreshes the views that read them."""
    values = ",\n".join(
        f"({instrument_id}, {price:.4f}, {'NULL' if volume is None else volume}, "
        f"TIMESTAMP '{quoted_at.strftime('%Y-%m-%d %H:%M:%S')}', '{source}')"
        for instrument_id, price, volume, quoted_at in rows
    )
    run_psql(args, f"""
    INSERT INTO price_quotes (instrument_id, price, volume, quote_timestamp, source)
    VALUES
    {values}
    -- A quote already stored for that instrument and second is the same quote; keep the first
    ON CONFLICT (instrument_id, quote_timestamp) DO NOTHING;

    -- CONCURRENTLY: the platform keeps reading prices while the views refresh
    REFRESH MATERIALIZED VIEW CONCURRENTLY latest_price_quotes;
    REFRESH MATERIALIZED VIEW CONCURRENTLY account_valuations;
    """, capture_output=False)


# ---------------------------------------------------------------------------------------------------------
# Market hours (same rules as MarketHours.java; exchange holidays aren't listed)
# ---------------------------------------------------------------------------------------------------------

def is_open(instrument: Instrument, at: datetime) -> bool:
    """at is an aware datetime."""
    if instrument.asset_class == "Crypto":
        return True
    if instrument.asset_class == "FX":
        ny = at.astimezone(NEW_YORK)
        day, before_rollover = ny.weekday(), ny.time() < FX_ROLLOVER
        # Closed from Friday 17:00 to Sunday 17:00 New York time
        return not (day == 5 or (day == 6 and before_rollover) or (day == 4 and not before_rollover))
    zone, opens, closes = SESSIONS.get(instrument.market, SESSIONS["US"])
    local = at.astimezone(zone)
    return local.weekday() < 5 and opens <= local.time() < closes


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def to_local_naive(at: datetime) -> datetime:
    """The database stores quote times as the server's local time without a zone, like the backend reads them."""
    return at.astimezone().replace(tzinfo=None)


# ---------------------------------------------------------------------------------------------------------
# Live mode
# ---------------------------------------------------------------------------------------------------------

def get_yfinance():
    try:
        import yfinance
        # Set again after the import, which resets warning filters
        warnings.filterwarnings("ignore", category=DeprecationWarning)
        return yfinance
    except ModuleNotFoundError as error:
        raise RuntimeError("yfinance is not installed. Run 'python -m pip install -r scripts/requirements.txt'.") from error


class LiveFeed:
    """
    The latest real price of every instrument, kept current by Yahoo's streaming feed, with the chart API
    as a fallback while the stream is silent. Prices are converted to USD as they are written.
    """

    def __init__(self, instruments: list[Instrument]):
        self.yf = get_yfinance()
        self.instruments = instruments
        self.lock = threading.Lock()
        # symbol -> (price in the symbol's own currency, aware time of the trade)
        self.latest: dict[str, tuple[float, datetime]] = {}
        self.currency: dict[str, tuple[str, int]] = {}  # ticker -> (major currency, minor unit divisor)
        self.last_message_at = 0.0
        self.written_at: dict[int, datetime] = {
            i.instrument_id: i.latest_quote_at.astimezone(timezone.utc) if i.latest_quote_at else datetime.min.replace(tzinfo=timezone.utc)
            for i in instruments
        }

    # -- start-up snapshot -------------------------------------------------------------------------------

    def snapshot(self, symbols: list[str]) -> None:
        """The latest one-minute bar of each symbol, so prices are current from the start, and each currency."""
        for symbol in symbols:
            try:
                ticker = self.yf.Ticker(symbol)
                history = ticker.history(period="5d", interval="1m", auto_adjust=False, actions=False, raise_errors=False)
                metadata = getattr(ticker, "history_metadata", None) or {}
                currency = metadata.get("currency") or "USD"
                self.currency[symbol] = MINOR_UNITS.get(currency, (currency, 1))
                closes = history["Close"].dropna() if not history.empty else []
                if len(closes):
                    # A bar is stamped with its start; its close is the last trade in that minute (the last
                    # bar of a session starts a minute before the close, so this stays inside the session)
                    bar_end = closes.index[-1].to_pydatetime().astimezone(timezone.utc) + timedelta(seconds=59)
                    traded_at = min(bar_end, now_utc()).replace(microsecond=0)
                    with self.lock:
                        previous = self.latest.get(symbol)
                        if previous is None or traded_at > previous[1]:
                            self.latest[symbol] = (float(closes.iloc[-1]), traded_at)
            except Exception as error:  # one bad symbol shouldn't stop the others
                print(f"Snapshot of {symbol} failed: {error}", file=sys.stderr, flush=True)

    # -- streaming ---------------------------------------------------------------------------------------

    def on_message(self, message: dict) -> None:
        symbol, price, epoch_ms = message.get("id"), message.get("price"), message.get("time")
        if not symbol or price is None or epoch_ms is None:
            return
        traded_at = datetime.fromtimestamp(int(epoch_ms) / 1000, tz=timezone.utc)
        with self.lock:
            previous = self.latest.get(symbol)
            if previous is None or traded_at >= previous[1]:
                self.latest[symbol] = (float(price), traded_at)
            self.last_message_at = time.monotonic()

    def stream_forever(self, symbols: list[str]) -> None:
        """Runs in a background thread; reconnects with a growing pause if the connection drops."""
        backoff = 1
        while True:
            try:
                socket = self.yf.WebSocket(verbose=False)
                socket.subscribe(symbols)
                print(f"Streaming live prices for {len(symbols)} symbols from Yahoo Finance", flush=True)
                backoff = 1
                socket.listen(self.on_message)
            except Exception as error:
                print(f"Live price stream dropped ({error}); reconnecting in {backoff}s", file=sys.stderr, flush=True)
            time.sleep(backoff)
            backoff = min(backoff * 2, 60)

    def stream_is_silent(self) -> bool:
        return time.monotonic() - self.last_message_at > STREAM_SILENCE_SECONDS

    def poll(self, instruments: list[Instrument], conversion_symbols: list[str]) -> None:
        """Fallback while the stream is silent: the latest one-minute bar from the chart API."""
        self.snapshot([i.ticker for i in instruments] + conversion_symbols)

    # -- writing -----------------------------------------------------------------------------------------

    def usd_price(self, instrument: Instrument, price: float) -> float | None:
        currency, divisor = self.currency.get(instrument.ticker, ("USD", 1))
        price = price / divisor
        if currency == "USD":
            return price
        # <CCY>=X is units of that currency per US dollar (e.g. GBP=X is 0.75)
        rate = self.latest.get(f"{currency}=X")
        if rate is None or rate[0] <= 0:
            return None
        return price / rate[0]

    def new_rows(self) -> list[tuple[int, float, int | None, datetime]]:
        """Each instrument whose price has a newer trade than the last one written, if traded in its session."""
        rows = []
        with self.lock:
            for instrument in self.instruments:
                tick = self.latest.get(instrument.ticker)
                if tick is None:
                    continue
                price, traded_at = tick
                # Skip what we already wrote, and pre/after-market trades outside the regular session
                if traded_at <= self.written_at[instrument.instrument_id] or not is_open(instrument, traded_at):
                    continue
                usd = self.usd_price(instrument, price)
                if usd is None or usd <= 0:
                    continue
                self.written_at[instrument.instrument_id] = traded_at
                rows.append((instrument.instrument_id, round(usd, 4), None, to_local_naive(traded_at)))
        return rows


def run_live(args: argparse.Namespace, instruments: list[Instrument], interval_seconds: int, iteration_limit: int | None) -> int:
    feed = LiveFeed(instruments)
    tickers = [i.ticker for i in instruments]
    print("Reading the latest price and currency of each instrument...", flush=True)
    feed.snapshot(tickers)
    conversion_symbols = sorted({f"{currency}=X" for currency, _ in feed.currency.values() if currency != "USD"})
    feed.snapshot(conversion_symbols)

    # The start-up snapshot is written as it is: the last real price, at the time it traded
    rows = feed.new_rows()
    if rows:
        write_quotes(args, rows, "YAHOO_LIVE")
        print(f"Wrote the latest real price of {len(rows)} instruments", flush=True)

    threading.Thread(target=feed.stream_forever, args=(tickers + conversion_symbols,), daemon=True).start()

    iteration = 0
    last_poll = time.monotonic()
    while True:
        time.sleep(interval_seconds)
        now = now_utc()
        open_instruments = [i for i in instruments if is_open(i, now)]
        if open_instruments and feed.stream_is_silent() and time.monotonic() - last_poll >= POLL_SECONDS:
            print("Live stream is silent; polling Yahoo's chart API instead", flush=True)
            feed.poll(open_instruments, conversion_symbols)
            last_poll = time.monotonic()

        rows = feed.new_rows()
        if rows:
            write_quotes(args, rows, "YAHOO_LIVE")
            iteration += 1
            names = ", ".join(next(i.ticker for i in instruments if i.instrument_id == r[0]) for r in rows[:6])
            more = f" and {len(rows) - 6} more" if len(rows) > 6 else ""
            print(f"[{datetime.now():%H:%M:%S}] {len(rows)} live prices: {names}{more}", flush=True)
            if iteration_limit is not None and iteration >= iteration_limit:
                return 0
        elif not open_instruments:
            print(f"[{datetime.now():%H:%M:%S}] every market is closed; waiting", flush=True)
            time.sleep(max(60 - interval_seconds, 0))


# ---------------------------------------------------------------------------------------------------------
# Simulated mode
# ---------------------------------------------------------------------------------------------------------

def simulate_price(instrument: Instrument, interval_seconds: int) -> tuple[float, int]:
    profile = ASSET_PROFILES.get(instrument.asset_class, ASSET_PROFILES["Equity"])
    drift, volatility, volume_jitter = profile["drift"], profile["volatility"], profile["volume_jitter"]
    if instrument.ticker in FX_TICKERS:
        drift, volatility, volume_jitter = 0.0, 0.00025, 0.015
    elif instrument.ticker in CRYPTO_TICKERS:
        drift, volatility, volume_jitter = 0.0004, 0.0025, 0.04

    scale = interval_seconds / BASE_INTERVAL_SECONDS
    move = random.gauss(drift * scale, volatility * math.sqrt(scale))
    instrument.price = round(max(instrument.price * (1 + move), 0.0001), 4)
    instrument.volume = max(int(instrument.volume * (1 + random.gauss(0, volume_jitter))), 1)
    return instrument.price, instrument.volume


def run_simulated(args: argparse.Namespace, instruments: list[Instrument], interval_seconds: int, iteration_limit: int | None) -> int:
    # A made-up starting price would stick forever, so only instruments that already have a quote move
    priced = [i for i in instruments if i.price is not None]
    if not priced:
        print("No instrument has a stored price yet (run yahoo_backfill.py first).", file=sys.stderr, flush=True)
        return 1
    print("SIMULATED prices: a random walk, labelled source = 'SIMULATED'. Not market data.", flush=True)

    iteration = 0
    while True:
        now = now_utc()
        moving = [i for i in priced if args.all_hours or is_open(i, now)]
        if moving:
            stamp = to_local_naive(now).replace(microsecond=0)
            rows = [(i.instrument_id, *simulate_price(i, interval_seconds), stamp) for i in moving]
            write_quotes(args, rows, "SIMULATED")
            iteration += 1
            print(f"[{stamp:%H:%M:%S}] {len(rows)} simulated prices (iteration {iteration})", flush=True)
            if iteration_limit is not None and iteration >= iteration_limit:
                return 0
        else:
            print(f"[{datetime.now():%H:%M:%S}] every market is closed; waiting (--all-hours to simulate anyway)", flush=True)
        time.sleep(interval_seconds)


def main() -> int:
    args = parse_args()
    interval_seconds = max(args.interval_seconds, 1)
    iteration_limit = args.iterations if args.iterations > 0 else None
    print(f"Quote generator ({args.source}) for {args.database} on {args.host}:{args.port}, "
          f"writing every {interval_seconds}s", flush=True)
    try:
        instruments = fetch_instruments(args)
        if not instruments:
            print("No instruments found; run the schema and seed first.", file=sys.stderr, flush=True)
            return 1
        if args.source == "live":
            return run_live(args, instruments, interval_seconds, iteration_limit)
        return run_simulated(args, instruments, interval_seconds, iteration_limit)
    except KeyboardInterrupt:
        print("Quote generator stopped.", flush=True)
        return 0
    except subprocess.CalledProcessError as error:
        print((error.stderr or str(error)).strip(), file=sys.stderr, flush=True)
        return error.returncode or 1
    except RuntimeError as error:
        print(str(error), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
