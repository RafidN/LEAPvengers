# Ticker Search - Parameterized Query Boilerplate

Minimal boilerplate for searching holdings by ticker using parameterized queries with JWT authentication.

## Backend Files

### DTOs
- **TickerSearchRequest.java** - Input from frontend (just ticker)
- **TickerSearchResult.java** - Response with holdings data

### Data Access Layer
- **TickerRepository.java** - Parameterized query that binds `:ticker` and `:clientId` safely

### Business Logic
- **TickerSearchService.java** - Validates input, gets user's clientId from JWT, executes query

### API Endpoint
- **TickerSearchController.java** - Extracts JWT token, calls service, returns results

## Frontend File

- **ticker-search.service.ts** - HTTP client that adds JWT token to request

## How It Works

1. **Frontend** calls `tickerSearchService.searchByTicker('AAPL')`
2. **Service** retrieves JWT from localStorage, adds to `Authorization: Bearer <token>` header
3. **POST /api/search/ticker** with `{ "ticker": "AAPL" }`
4. **Controller** extracts userId from JWT token
5. **Service** gets user's clientId from database
6. **Repository** executes parameterized query:
   ```sql
   SELECT h.holding_id, i.ticker, i.instrument_name, h.quantity, ...
   FROM holdings h
   JOIN instruments i ON h.instrument_id = i.instrument_id
   JOIN accounts a ON h.account_id = a.account_id
   WHERE a.client_id = :clientId
     AND UPPER(i.ticker) LIKE UPPER(CONCAT('%', :ticker, '%'))
   ```
   - `:clientId` bound to parameter (user's client from JWT)
   - `:ticker` bound to parameter (user input from request)
   - No string concatenation = SQL injection prevention
7. **Results returned** - only user's data (filtered by clientId)

## Usage

### Backend - Register service and repository in Spring
```java
// Already injected via @Service and @Repository
```

### Frontend - Inject and call service
```typescript
export class MyComponent {
  constructor(private tickerSearch: TickerSearchService) {}

  search(ticker: string) {
    this.tickerSearch.searchByTicker(ticker).subscribe(
      results => console.log('Found holdings:', results),
      error => console.error('Search failed:', error)
    );
  }
}
```

## Security Features

✅ JWT token required (Authorization header)
✅ Parameterized query (`:ticker` and `:clientId`)
✅ User isolation (clientId from JWT, not request)
✅ Input validation (backend validates ticker)
✅ SQL injection prevention (no string concatenation)

## Example Request

```
POST /api/search/ticker HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "ticker": "AAPL"
}
```

## Example Response

```json
[
  {
    "holdingId": 1,
    "ticker": "AAPL",
    "instrumentName": "Apple Inc.",
    "quantity": 100,
    "marketValue": 18250.00
  },
  {
    "holdingId": 2,
    "ticker": "AAPL",
    "instrumentName": "Apple Inc.",
    "quantity": 50,
    "marketValue": 8000.00
  }
]
```

## Key Security Points

1. **Parameterized Queries**: Spring Data JPA binds `:ticker` as a parameter value, not SQL code
2. **clientId from JWT**: Cannot be spoofed - extracted from validated token, not request body
3. **User Validation**: Service confirms user exists before executing query
4. **Input Validation**: Checks ticker length and format before passing to query
5. **Token Validation**: JwtUtil validates signature and expiration before processing

## How to Adapt for Other Entities

Replace "ticker" with your search parameter:
- **Order search**: `@Param("orderType")` for "BUY"/"SELL"
- **Account search**: `@Param("accountNumber")` for account lookup
- **Client search**: `@Param("clientName")` for name search

Same pattern applies - always use `:paramName` and `@Param("paramName")`
