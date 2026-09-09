# Plan: JWT Authorization Backend Workflow for Stock Trading App

## TL;DR
Build a complete JWT authentication system with 4 endpoints (login, logout, account creation, forgot password) using the existing enterprise-schema and Spring Boot setup. The system will:
- Hash passwords with BCrypt
- Generate JWT tokens on login
- Validate tokens on protected endpoints  
- Allow team to build login UI against REST API
- Use Users → Accounts → Clients relationship structure already in database

---

## Architecture Overview

```
Client (Login UI)
    ↓
POST /api/auth/login {username, password, account_id}
    ↓
AuthController → AuthService
    ↓
UserRepository (query by username + account_id)
    ↓
Password match? BCryptPasswordEncoder.matches()
    ↓ YES
JwtUtil.generateToken(user) → JWT token
    ↓
Return {token, user details}
    ↓
Client stores token (localStorage/session)
    ↓
Subsequent requests: Authorization: Bearer {token}
    ↓
JwtAuthenticationFilter validates token
    ↓
Route to protected endpoint
```

---

## Steps

### Phase 1: Foundation Classes (Dependencies Ready)
These leverage existing pom.xml dependencies (JJWT 0.12.3, Spring Security Crypto).

1. **Create UserRepository** (`repository/UserRepository.java`)
   - `findByUsername(String username)` with query: `@Query("SELECT u FROM Users u WHERE u.username = ?1")`
   - `findByUsernameAndAccountId(String username, Integer accountId)` for account-specific login
   - Enables database queries for user lookup

2. **Create SecurityConfig** (`config/SecurityConfig.java`)
   - Wire BCryptPasswordEncoder bean with strength 10
   - Configure HttpSecurity (disable CORS/CSRF for JWT, stateless sessions)
   - Register JwtAuthenticationFilter in security chain
   - Allow /api/auth/* endpoints without authentication

3. **Create JwtUtil** (`security/JwtUtil.java`)
   - Inject `@Value("${jwt.secret}")` and `@Value("${jwt.expiration}")`
   - `generateToken(Users user)` → creates JWT with claims (userId, username, clientId, accountId)
   - `validateToken(String token)` → true if valid signature + not expired
   - `extractUsername(String token)`, `extractUserId(String token)`, etc.
   - Uses Jwts.builder() and Jwts.parserBuilder() from JJWT library

4. **Create JwtAuthenticationFilter** (`security/JwtAuthenticationFilter.java`)
   - Extends OncePerRequestFilter
   - Intercepts requests, extracts "Authorization: Bearer {token}" header
   - Validates token via JwtUtil
   - Sets SecurityContextHolder authentication for downstream controllers
   - Allows unauthenticated access to /api/auth/* paths

---

### Phase 2: API Layer & Business Logic
Connects UI requests to database operations.

5. **Create DTOs** (`model/dto/AuthenticationRequest.java`, `model/dto/AuthenticationResponse.java`)
   - AuthenticationRequest: {username, password, accountId}
   - AuthenticationResponse: {token, userId, username, clientId, accountId}
   - Supports front-end login form and response parsing

6. **Create AuthService** (`service/AuthService.java`)
   - `login(String username, String password, Integer accountId)` → AuthenticationResponse
     - Query UserRepository for user by (username + accountId)
     - Throw UserNotFoundException if not found
     - Compare plaintext password with BCrypt hash via passwordEncoder.matches()
     - Throw InvalidCredentialsException if mismatch
     - Generate JWT token via JwtUtil.generateToken()
     - Return AuthenticationResponse with token
   - `registerAccount(Client client, String username, String password, Account account)` → User
     - Create new Users record
     - Hash password with BCryptPasswordEncoder
     - Save to database via UserRepository
   - `forgotPassword(String username, String email)` → mock/email logic (can return placeholder)

7. **Create AuthController** (`controller/AuthController.java`)
   - `POST /api/auth/login` → calls AuthService.login() → returns token
   - `POST /api/auth/register` → calls AuthService.registerAccount() → returns user
   - `POST /api/auth/forgot-password` → calls AuthService.forgotPassword() → returns status
   - `POST /api/auth/logout` → no DB operation (token expires, client clears storage)
   - Exception handling: catch UserNotFoundException, InvalidCredentialsException → return 401/400

---

### Phase 3: Error Handling & Security

8. **Create Custom Exceptions** (`exception/AuthenticationException.java`, etc.)
   - InvalidCredentialsException (extends RuntimeException)
   - UserNotFoundException
   - TokenValidationException
   - All inherit from a base exception for consistent handling

9. **Enhance GlobalExceptionHandler** 
   - Add `@ExceptionHandler(InvalidCredentialsException.class)` → 401 Unauthorized
   - Add `@ExceptionHandler(UserNotFoundException.class)` → 404 Not Found
   - Add `@ExceptionHandler(TokenValidationException.class)` → 401 Unauthorized
   - Returns error response DTO with message and timestamp

---

### Phase 4: Protected Endpoints Setup
Placeholder for team to add later (not in scope, but shows integration point).

10. **Example: Protected /api/accounts Endpoint**
    - `@GetMapping("/api/accounts/me")` — returns accounts for authenticated user
    - `@PreAuthorize("hasRole('USER')")` or filter by SecurityContextHolder.getContext().getAuthentication()
    - JwtAuthenticationFilter ensures only valid tokens reach here
    - Demonstrates how other endpoints will use the JWT

---

## Relevant Files

### Files to Create (9)
- `backend/src/main/java/com/neueda/leap/repository/UserRepository.java`
- `backend/src/main/java/com/neueda/leap/config/SecurityConfig.java`
- `backend/src/main/java/com/neueda/leap/security/JwtUtil.java`
- `backend/src/main/java/com/neueda/leap/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/neueda/leap/service/AuthService.java`
- `backend/src/main/java/com/neueda/leap/controller/AuthController.java`
- `backend/src/main/java/com/neueda/leap/model/dto/AuthenticationRequest.java`
- `backend/src/main/java/com/neueda/leap/model/dto/AuthenticationResponse.java`
- `backend/src/main/java/com/neueda/leap/exception/AuthenticationException.java` (+ UserNotFoundException, InvalidCredentialsException)

### Files to Modify (1)
- `backend/src/main/java/com/neueda/leap/exception/GlobalExceptionHandler.java` — add @ExceptionHandler methods

### Database (No Changes Needed)
- Users model already has username, password, account_id, client_id ✓
- Foreign keys to Accounts and Clients already in place ✓
- Unique constraint (username, account_id) prevents duplicate logins per account ✓

---

## Verification

### Phase 1 Verification (Security Setup)
1. `mvn clean compile` passes
2. SecurityConfig creates BCryptPasswordEncoder bean (confirm via Spring logs at startup)
3. JwtUtil can generate and validate tokens (unit test: `testGenerateAndValidateToken()`)

### Phase 2 Verification (API Layer)
1. Start backend: `mvn spring-boot:run` on port 8081
2. Test POST /api/auth/login with valid credentials (create test user in DB first)
   - Expect: 200 OK with {token, userId, username, ...}
3. Test POST /api/auth/login with invalid password
   - Expect: 401 Unauthorized with error message
4. Test POST /api/auth/login with nonexistent username
   - Expect: 404 Not Found with error message
5. Test POST /api/auth/register with new user details
   - Expect: 201 Created with user record
6. Use token from login to test protected endpoint (mock endpoint at /api/auth/validate)
   - Expect: 200 OK if token valid, 401 if expired/invalid

### Phase 3 Verification (Security)
1. Remove token from Authorization header on protected request → 401
2. Modify token (change character) → 401
3. Wait for token to expire (test with low expiration in dev) → 401

### Team Integration Verification
1. Frontend team uses /api/auth/login endpoint
2. Frontend successfully stores JWT token
3. Frontend successfully sends token in Authorization header
4. Protected endpoints (if created) reject requests without valid token

---

## Decisions

- **Password Hashing**: BCryptPasswordEncoder with strength 10 (consistent with existing pom.xml setup)
- **Token Expiration**: 1 hour (from application.properties `jwt.expiration=3600000`)
- **Login Identifier**: (username + account_id) — allows same username for different accounts/portfolios
- **Stateless**: JWT token validated on every request (no session storage)
- **Logout**: Client-side only (token expires after 1 hour; client clears localStorage)
- **Forgot Password**: Placeholder implementation (real implementation requires email service, not in scope)

---

## Further Considerations

1. **Refresh Tokens**: Should we implement a refresh token endpoint to allow long-lived sessions without expiring short-lived access tokens? (Recommendation: add later if needed)

2. **Role-Based Access Control (RBAC)**: Should we add roles (ADMIN, USER, VIEW_ONLY) to the Users table or create a separate Roles junction table? (Recommendation: defer to Phase 2 of backend if required by team)

3. **Rate Limiting**: Should we add login attempt throttling to prevent brute-force attacks? (Recommendation: add to SecurityConfig after Phase 1 if security team requires it)

4. **Email Verification**: Should new accounts require email verification before access? (Recommendation: defer to Phase 2 if signup flow requires it)

5. **Audit Logging**: Should we log all login attempts (success/failure) for security audits? (Recommendation: add to AuthService after Phase 1 if compliance requires it)
