# Client Segment Feature

## Overview

This feature classifies clients into four analyst-facing segments based on two live inputs:

- total portfolio value
- recent filled-order activity

The segment is derived at query time. It is not stored as a permanent column on the client record.

This keeps the implementation aligned with the existing backend query style in this project:

- controller accepts the request and handles auth/errors
- service validates inputs and applies business rules
- repository performs the data fetch and aggregation

## Segment Labels

The current labels are:

- `Dormant`
- `Core`
- `Active`
- `Premier`

These labels are intended to separate low-value/low-activity clients from steady clients, high-activity clients, and high-value/high-activity clients.

## Classification Model

The feature uses a two-dimensional model instead of a single ratio.

- Portfolio value comes from summed account valuations for a client.
- Order activity comes from a count of recent `Filled` orders over a configurable lookback window.

This is more reliable than using a single formula such as portfolio value divided by order frequency, because the two-dimensional model preserves the difference between:

- high value and high activity
- medium value and high activity
- low value and low activity

## Default Rules

If no overrides are provided in the request, the service uses these defaults:

- `lookbackDays = 90`
- `dormantMaxPortfolioValue = 25000`
- `dormantMaxOrderCount = 1`
- `premierMinPortfolioValue = 250000`
- `activeMinOrderCount = 12`

Classification logic:

1. `Premier`
   Client has portfolio value greater than or equal to `premierMinPortfolioValue` and recent filled-order count greater than or equal to `activeMinOrderCount`.
2. `Active`
   Client has recent filled-order count greater than or equal to `activeMinOrderCount`, but does not meet the `Premier` rule.
3. `Dormant`
   Client has portfolio value less than or equal to `dormantMaxPortfolioValue` and recent filled-order count less than or equal to `dormantMaxOrderCount`.
4. `Core`
   Any client that does not meet the previous rules.

## Backend Flow

### Controller

Path:

- `POST /api/clients/segments`

The controller:

- requires a JWT bearer token
- extracts `userId` from the token
- delegates to the service
- returns standard HTTP responses for validation, auth, and server errors

### Service

The service:

- validates that the requesting user exists
- resolves request overrides or defaults
- validates threshold values
- fetches aggregated metrics from the repository
- classifies each client into a segment
- optionally filters by a requested segment
- sorts results by segment rank, portfolio value, order count, and client name

### Repository

The repository uses a native query with two aggregations:

- `client_portfolio`
  Sums `account_valuations.total_value` across all accounts belonging to each client.
- `client_orders`
  Counts recent `Filled` orders across all accounts belonging to each client.

The query then joins those aggregates back to `clients` so each client can be returned even if they currently have zero recent orders or zero valuation.

## Data Sources

The feature relies on these schema objects:

- `clients`
- `accounts`
- `orders`
- `account_valuations`

Notes:

- Portfolio value is read from the materialized view `account_valuations`.
- Order activity is calculated lazily from `orders` using `submitted_at` and `order_status = 'Filled'`.

## Request Contract

Body fields are optional.

```json
{
  "segment": "Active",
  "lookbackDays": 90,
  "dormantMaxPortfolioValue": 25000,
  "dormantMaxOrderCount": 1,
  "premierMinPortfolioValue": 250000,
  "activeMinOrderCount": 12
}
```

Field meanings:

- `segment`
  Optional filter. Must be one of `Dormant`, `Core`, `Active`, or `Premier`.
- `lookbackDays`
  Rolling window used to count recent filled orders.
- `dormantMaxPortfolioValue`
  Upper portfolio-value threshold for `Dormant`.
- `dormantMaxOrderCount`
  Upper recent-order threshold for `Dormant`.
- `premierMinPortfolioValue`
  Lower portfolio-value threshold for `Premier`.
- `activeMinOrderCount`
  Lower recent-order threshold for `Active` and `Premier`.

If the request body is omitted, backend defaults are used.

## Response Shape

```json
[
  {
    "clientId": 12,
    "firstName": "Ada",
    "lastName": "Lovelace",
    "email": "ada@example.com",
    "totalPortfolioValue": 420000.50,
    "recentFilledOrderCount": 18,
    "segment": "Premier"
  }
]
```

## Sorting Behavior

Results are sorted in this order:

1. segment rank
2. higher portfolio value first
3. higher recent filled-order count first
4. last name
5. first name
6. client id

Segment rank is currently:

1. `Dormant`
2. `Core`
3. `Active`
4. `Premier`

## Validation Rules

The service rejects requests when:

- `lookbackDays <= 0`
- any portfolio threshold is negative
- any order-count threshold is negative
- `premierMinPortfolioValue <= dormantMaxPortfolioValue`
- `segment` is not one of the supported labels

## Performance Notes

This feature is designed for lazy analyst-style queries rather than high-frequency end-user traffic.

Why this is acceptable:

- only a small number of platform analysts are expected to use it
- portfolio value is already precomputed in `account_valuations`
- order counting is bounded by a lookback window

Supporting index added to the schema:

- `orders_account_status_submitted_at_idx` on `(account_id, order_status, submitted_at DESC)`

This helps the recent filled-order count query avoid unnecessary scanning when filtering by account, status, and time window.

## Frontend Integration

An Angular service was added for this feature:

- `frontend/src/app/services/client-segment.service.ts`

It sends authenticated `POST` requests to `/api/clients/segments` and exposes typed request and response interfaces for the frontend.

## Security Note

The endpoint is authenticated, but not yet role-restricted.

If this data should be limited to analysts or admins, authorization rules should be added at the backend before exposing the feature broadly in the UI.