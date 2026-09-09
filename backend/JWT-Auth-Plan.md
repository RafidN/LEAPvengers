# Plan: JWT Authorization Backend Workflow for Stock Trading App (Option A)

## TL;DR
Build a complete JWT authentication system with 4 endpoints (register, login, logout, forgot password). Each user logs in with a single credential and accesses all accounts under their client.

**User Model:** 1 Client → Multiple Accounts, 1 Login per Person

---

## Architecture Overview

```
User Registration (firstName, lastName, email, username, password)
    ↓
AuthController → AuthService
    ↓
Create Client record + Create User record (tied to client)
    ↓
Return user details
    
---

User Login (username, password)
    ↓
AuthController → AuthService
    ↓
UserRepository (query by username)
    ↓
Password match? BCryptPasswordEncoder.matches()
    ↓ YES
JwtUtil.generateToken(user) → JWT token with {userId, clientId}
    ↓
Return {token, userId, username, clientId}
    ↓
Client stores token
    ↓
Subsequent requests: Authorization: Bearer {token}
    ↓
JwtAuthenticationFilter validates token
    ↓
Extract clientId from token → Query all accounts for that client
    ↓
Route to protected endpoint (user can access all their client's accounts)
```

---

## Implementation Steps

### Phase 1: Foundation Classes (Security Setup)

1. **Create UserRepository** (`repository/UserRepository.java`)
   - `findByUsername(String username)` — query user by username
   - `existsByUsername(String username)` — check if username exists (for validation during registration)
   - Enables database queries for user lookup

2. **Create SecurityConfig** (`config/SecurityConfig.java`)
   - Wire BCryptPasswordEncoder bean with strength 10
   - Configure HttpSecurity:
     - Disable CORS/CSRF for JWT
     - Set session management to STATELESS
     - Allow `/api/auth/**` endpoints without authentication
   - Register JwtAuthenticationFilter in security chain
   - Configure authentication manager

3. **Create JwtUtil** (`security/JwtUtil.java`)
   - Inject `@Value("${jwt.secret}")` and `@Value("${jwt.expiration}")`
   - `generateToken(Users user)` → creates JWT with claims:
     - `userId`
     - `username`
     - `clientId`
   - `validateToken(String token)` → true if valid signature + not expired
   - `extractUsername(String token)`, `extractUserId(String token)`, `extractClientId(String token)`
   - Uses Jwts.builder() and Jwts.parserBuilder() from JJWT library
   - Handle `SignatureException`, `MalformedJwtException`, `ExpiredJwtException`

4. **Create JwtAuthenticationFilter** (`security/JwtAuthenticationFilter.java`)
   - Extends OncePerRequestFilter
   - Intercepts requests, extracts "Authorization: Bearer {token}" header
   - Validates token via JwtUtil
   - Sets SecurityContextHolder authentication with extracted claims for downstream controllers
   - Allows unauthenticated access to `/api/auth/**` paths
   - Silently skips invalid tokens on protected paths (returns 401)

---

### Phase 2: API Layer & Business Logic

5. **Create DTOs** 
   - `model/dto/RegisterRequest.java`: {firstName, lastName, email, username, password}
   - `model/dto/LoginRequest.java`: {username, password}
   - `model/dto/AuthenticationResponse.java`: {token, userId, username, clientId, email}
   - `model/dto/ForgotPasswordRequest.java`: {username, email}
   - `model/dto/ForgotPasswordResponse.java`: {status, message}

6. **Create AuthService** (`service/AuthService.java`)
   - `register(RegisterRequest request)` → AuthenticationResponse
     - Validate: username not already exists (query UserRepository)
     - Throw InvalidInputException if username exists
     - Create new Client record (firstName, lastName, email)
     - Create new User record (clientId, username, hashed_password)
     - Return AuthenticationResponse with token
   
   - `login(String username, String password)` → AuthenticationResponse
     - Query UserRepository for user by username
     - Throw UserNotFoundException if not found
     - Compare plaintext password with BCrypt hash via passwordEncoder.matches()
     - Throw InvalidCredentialsException if mismatch
     - Generate JWT token via JwtUtil.generateToken()
     - Return AuthenticationResponse with token + clientId
   
   - `forgotPassword(String username, String email)` → ForgotPasswordResponse
     - Query user by username + check email matches client email
     - Return placeholder response (real implementation requires email service)
     - Recommendation: implement in Phase 2

7. **Create AuthController** (`controller/AuthController.java`)
   - `POST /api/auth/register` {firstName, lastName, email, username, password}
     - Calls AuthService.register()
     - Returns 201 Created with token + user details
     - Exception: InvalidInputException → 400 Bad Request
   
   - `POST /api/auth/login` {username, password}
     - Calls AuthService.login()
     - Returns 200 OK with token + user details
     - Exception: UserNotFoundException → 404 Not Found
     - Exception: InvalidCredentialsException → 401 Unauthorized
   
   - `POST /api/auth/forgot-password` {username, email}
     - Calls AuthService.forgotPassword()
     - Returns 200 OK with status message
   
   - `POST /api/auth/logout` (requires auth header)
     - No DB operation (token expires, client clears storage)
     - Returns 200 OK with status message
   
   - `GET /api/auth/validate` (requires auth header)
     - Validates token is still valid
     - Returns 200 OK with {valid: true, userId, clientId}
     - Returns 401 if token invalid/expired

---

### Phase 3: Error Handling & Exception Classes

8. **Create Custom Exceptions** (`exception/` folder)
   - `InvalidCredentialsException` (extends RuntimeException)
   - `UserNotFoundException` (extends RuntimeException)
   - `TokenValidationException` (extends RuntimeException)
   - `InvalidInputException` (extends RuntimeException)

9. **Enhance GlobalExceptionHandler** (`exception/GlobalExceptionHandler.java`)
   - Add `@ExceptionHandler(InvalidCredentialsException.class)` → 401 Unauthorized
   - Add `@ExceptionHandler(UserNotFoundException.class)` → 404 Not Found
   - Add `@ExceptionHandler(TokenValidationException.class)` → 401 Unauthorized
   - Add `@ExceptionHandler(InvalidInputException.class)` → 400 Bad Request
   - All return error response DTO: {status, message, timestamp}

---

### Phase 4: Protected Endpoints (Phase 2 of team development)

10. **Example: Protected /api/accounts Endpoint** (for team reference)
    - `GET /api/accounts` (requires auth header)
      - Extract clientId from JWT token
      - Query all accounts WHERE client_id = extracted_clientId
      - Returns list of accounts user can access
    - `POST /api/accounts` (requires auth header)
      - Extract clientId from JWT token
      - Create new Account under that client
      - Returns newly created account
    - Demonstrates how other endpoints will use JWT token + clientId

---

## Relevant Files

### Files to Create (11)
1. `backend/src/main/java/com/neueda/leap/repository/UserRepository.java`
2. `backend/src/main/java/com/neueda/leap/config/SecurityConfig.java`
3. `backend/src/main/java/com/neueda/leap/security/JwtUtil.java`
4. `backend/src/main/java/com/neueda/leap/security/JwtAuthenticationFilter.java`
5. `backend/src/main/java/com/neueda/leap/service/AuthService.java`
6. `backend/src/main/java/com/neueda/leap/controller/AuthController.java`
7. `backend/src/main/java/com/neueda/leap/model/dto/RegisterRequest.java`
8. `backend/src/main/java/com/neueda/leap/model/dto/LoginRequest.java`
9. `backend/src/main/java/com/neueda/leap/model/dto/AuthenticationResponse.java`
10. `backend/src/main/java/com/neueda/leap/model/dto/ForgotPasswordRequest.java`
11. `backend/src/main/java/com/neueda/leap/model/dto/ForgotPasswordResponse.java`

### Files to Modify (3)
1. `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java` — add auth exception handlers
2. `backend/src/main/java/com/neueda/leap/model/Users.java` ✅ (DONE)
3. `backend/Database/enterprise-schema.sql` ✅ (DONE)

### Exception Classes to Create (4)
- `exception/InvalidCredentialsException.java`
- `exception/UserNotFoundException.java`
- `exception/TokenValidationException.java`
- `exception/InvalidInputException.java`

---

## Database Schema Changes ✅

**Updated Users Table:**
```sql
CREATE TABLE users (
    user_id               SERIAL PRIMARY KEY,
    client_id             INTEGER NOT NULL REFERENCES clients(client_id),
    username              TEXT NOT NULL UNIQUE,           -- ✅ Globally unique
    password              VARCHAR(255) NOT NULL,
    is_active             BOOLEAN DEFAULT true,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX users_username_idx ON users (username);
CREATE INDEX users_client_id_idx ON users (client_id);
```

**What Removed:**
- ❌ `account_id` column
- ❌ `UNIQUE (username, account_id)` constraint

**What Stays:**
- ✅ `client_id` (user tied to one client)
- ✅ `username UNIQUE` (globally unique login)
- ✅ Users can access all accounts of their client

---

## API Endpoints for Your Team

| Endpoint | Method | Auth Required | Input | Output | Purpose |
|----------|--------|---|-------|--------|---------|
| `/api/auth/register` | POST | No | {firstName, lastName, email, username, password} | {token, userId, username, clientId} | Create new user + client |
| `/api/auth/login` | POST | No | {username, password} | {token, userId, username, clientId} | Login to existing account |
| `/api/auth/logout` | POST | Yes | none | {status: "success"} | Clear token (client-side) |
| `/api/auth/forgot-password` | POST | No | {username, email} | {status, message} | Password reset (placeholder) |
| `/api/auth/validate` | GET | Yes | (header: Authorization: Bearer {token}) | {valid: true, userId, clientId} | Validate token |

---

## Workflow Summary

### User Registration
```
User visits signup → Enters firstName, lastName, email, username, password
→ POST /api/auth/register
→ AuthService creates Client + User
→ JwtUtil generates token
→ Response: {token, userId, clientId}
→ Frontend stores token in localStorage
```

### User Login
```
User visits login → Enters username, password
→ POST /api/auth/login
→ AuthService queries Users table by username
→ BCryptPasswordEncoder.matches(plaintext, hash) → true/false
→ If true: JwtUtil generates token
→ Response: {token, userId, clientId}
→ Frontend stores token in localStorage
```

### Accessing Multiple Accounts
```
User clicks "View Accounts" 
→ Frontend sends: GET /api/accounts with Authorization: Bearer {token}
→ JwtAuthenticationFilter validates token, extracts clientId
→ AccountController queries: SELECT * FROM accounts WHERE client_id = ?
→ Returns all accounts for that user's client
→ User can switch between accounts (all are accessible)
```

### Creating New Account (Portfolio)
```
User clicks "Open New Account"
→ Frontend sends: POST /api/accounts with account details + Authorization: Bearer {token}
→ JwtAuthenticationFilter validates token, extracts clientId
→ AccountService creates Account record with client_id from token
→ Account is immediately accessible to user
```

---

## Verification Steps

### Phase 1: Compilation & Security Setup
1. ✅ mvn clean compile passes
2. ✅ SecurityConfig creates BCryptPasswordEncoder bean
3. ✅ JwtUtil can generate and validate tokens
4. Start app: `mvn spring-boot:run`
5. Check logs: "SecurityConfig initialized" or similar

### Phase 2: API Endpoints
1. **Register new user**
   ```bash
   POST http://localhost:8081/api/auth/register
   {
     "firstName": "John",
     "lastName": "Doe",
     "email": "john@example.com",
     "username": "johndoe",
     "password": "password123"
   }
   ```
   Expected: 201 Created with {token, userId, clientId}

2. **Login with that user**
   ```bash
   POST http://localhost:8081/api/auth/login
   {
     "username": "johndoe",
     "password": "password123"
   }
   ```
   Expected: 200 OK with {token, userId, clientId}

3. **Invalid password**
   ```bash
   POST http://localhost:8081/api/auth/login
   {
     "username": "johndoe",
     "password": "wrongpassword"
   }
   ```
   Expected: 401 Unauthorized with error message

4. **Nonexistent user**
   ```bash
   POST http://localhost:8081/api/auth/login
   {
     "username": "nonexistent",
     "password": "password123"
   }
   ```
   Expected: 404 Not Found with error message

5. **Validate token**
   ```bash
   GET http://localhost:8081/api/auth/validate
   Authorization: Bearer {token_from_login}
   ```
   Expected: 200 OK with {valid: true, userId, clientId}

6. **Protected endpoint without token**
   ```bash
   GET http://localhost:8081/api/auth/validate
   ```
   Expected: 401 Unauthorized

### Phase 3: Error Handling
1. Remove Authorization header from protected request → 401
2. Modify token (change any character) → 401
3. Wait for token to expire → 401

### Phase 4: Frontend Integration
1. Frontend team uses `/api/auth/register` to create accounts
2. Frontend team uses `/api/auth/login` to authenticate
3. Frontend successfully stores JWT token
4. Frontend successfully sends token in Authorization header on subsequent requests
5. Protected endpoints work with token, fail without token

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Username UNIQUE (globally) | Simplicity. Each person has 1 login across all accounts. |
| User tied to Client (not Account) | Flexibility. User can manage multiple portfolios under one client. |
| JWT Token contains clientId | Enables queries like "get all accounts for this clientId" without DB roundtrips. |
| 1-hour expiration | Balance between security (short-lived) and UX (not too frequent re-login). |
| BCrypt strength 10 | Standard security + performance trade-off. |
| Stateless (no session DB) | Scales horizontally; JWT validated on every request. |
| Logout is client-side | Token expires naturally after 1 hour; frontend clears localStorage. |

---

## Further Considerations (Phase 2+)

1. **Refresh Tokens?**
   - Allow long-lived sessions without 1-hour re-login
   - Recommendation: Implement if frontend requires it after testing Phase 1

2. **Email Verification on Signup?**
   - Require email confirmation before account activation
   - Recommendation: Add in Phase 2 if security team requires it

3. **Rate Limiting on Login?**
   - Throttle failed login attempts to prevent brute-force
   - Recommendation: Add to SecurityConfig if security audit requires it

4. **Audit Logging?**
   - Log all login attempts (success + failure) for compliance
   - Recommendation: Add to AuthService after Phase 1 if required

5. **Real Forgot Password Flow?**
   - Send email with reset link instead of placeholder
   - Recommendation: Implement in Phase 2 with email service (SendGrid, AWS SES, etc.)

6. **Role-Based Access Control (RBAC)?**
   - Add roles (ADMIN, USER, VIEW_ONLY) to Users table or separate Roles table
   - Recommendation: Defer to Phase 2+ if different permission levels needed
