# Market data and database jobs: the live price feed, the Yahoo Finance history load, and first-time database
# set-up (see docker-entrypoint.sh). Build context: the repository root, for database/*.sql.
#   docker build -f scripts/Dockerfile -t leap-market-data .
#   docker run ... leap-market-data init-db | feed | backfill

FROM python:3.12-slim
# Quote times are stored without a zone and compared with the server clock, so every container runs in UTC
ENV TZ=UTC \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY scripts/requirements.txt scripts/requirements.txt
RUN pip install -r scripts/requirements.txt
COPY database/enterprise-schema.sql database/seed.sql database/
COPY scripts/live_quote_generator.py scripts/yahoo_backfill.py scripts/
COPY --chmod=755 scripts/docker-entrypoint.sh scripts/docker-entrypoint.sh
# yfinance keeps a small cache in the home directory
RUN useradd --create-home --system leap
USER leap
ENTRYPOINT ["/app/scripts/docker-entrypoint.sh"]
CMD ["feed"]
