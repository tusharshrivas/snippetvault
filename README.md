# SnippetVault API

A production-grade REST API for storing, retrieving, and searching code snippets — built as the primary product, not a backend layer.

Developers register once, receive a JWT and a permanent API key, and interact entirely through HTTP. No frontend required.

**Live API:** `https://snippetvault.up.railway.app` *(replace after deployment)*
**Swagger UI:** `https://snippetvault.up.railway.app/swagger-ui.html`

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Build | Maven |
| Database | PostgreSQL 15 |
| ORM | Spring Data JPA + Hibernate |
| Auth | Spring Security + JWT (jjwt 0.12.5) |
| Rate Limiting | Redis 7 (atomic INCR) |
| API Docs | Springdoc OpenAPI / Swagger UI |
| Containerisation | Docker + Docker Compose |
| Deployment | Railway (free tier) |
| Testing | JUnit 5 + Mockito (37 tests) |

---

## Architecture Overview

### Request Lifecycle

Every HTTP request passes through a three-stage filter chain before reaching a controller:

```
Incoming Request
       │
       ▼
┌─────────────────────────┐
│  ApiKeyAuthFilter       │  Checks X-API-Key header → loads user → sets SecurityContext
└────────────┬────────────┘
             │ (skipped if SecurityContext already set)
             ▼
┌─────────────────────────┐
│  JwtAuthFilter          │  Extracts Bearer token → validates → sets SecurityContext
└────────────┬────────────┘
             │ (skipped if SecurityContext already set)
             ▼
┌─────────────────────────┐
│  RateLimitFilter        │  Reads SecurityContext identity → INCR Redis key → allow/reject
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  Spring Security Authz  │  Checks .anyRequest().authenticated() rules
└────────────┬────────────┘
             │
             ▼
        Controller
```

The filter chain is deliberately ordered: authentication filters run first so the rate limiter knows whether the caller is authenticated (100 req/min) or anonymous (10 req/min).

### JWT Strategy

Two token types are embedded via a `"type"` claim:

- **Access token** — 30 minutes, accepted on all protected endpoints
- **Refresh token** — 7 days, accepted only at `POST /api/auth/refresh`

The JWT filter rejects refresh tokens on regular endpoints by checking the `type` claim explicitly. This prevents a stolen refresh token from being used as a general credential.

Refresh token rotation is implemented: every call to `/refresh` issues a brand-new refresh token, making the previous one stale on the client.

### Rate Limiting with Redis

```
Request arrives
      │
      ▼
INCR rate_limit:{identifier}   ← atomic, no race condition possible
      │
      ├─ count == 1 → SET EXPIRE 60s   ← start the window TTL
      │
      ├─ count <= limit → pass through, set X-RateLimit-* headers
      │
      └─ count > limit  → HTTP 429, set Retry-After header
```

Keys expire automatically via Redis TTL — no background cleanup job needed. Using `INCR` instead of `GET + SET` eliminates the race condition that would allow concurrent requests to both read "limit-1" and both get through.

---

## Running Locally

### Prerequisites

- Docker Desktop (includes Docker Compose)
- Git

No Java or Maven installation required — the multi-stage Dockerfile handles the build inside a container.

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/tusharshrivas/snippetvault.git
cd snippetvault

# 2. Create your environment file
cp .env.example .env
# Optional: edit .env to set a stronger JWT_SECRET for local testing

# 3. Build and start all three services (app + postgres + redis)
docker-compose up --build

# 4. Wait for startup — you should see:
#    Started SnippetVaultApplication in X.XXX seconds

# 5. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

First build: ~3 minutes (Maven downloads ~200MB of dependencies into a cached layer).
Subsequent builds with source changes only: ~30 seconds.

### Stopping

```bash
docker-compose down          # stop containers, keep database volumes
docker-compose down -v       # stop containers AND wipe all data
```

---

## API Reference

### Authentication Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/auth/register` | None | Register new user, receive JWT + API key |
| POST | `/api/auth/login` | None | Login, receive JWT access + refresh tokens |
| POST | `/api/auth/refresh` | None | Exchange refresh token for new token pair |

### Snippet Endpoints

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| POST | `/api/snippets` | JWT or API Key | Create a new snippet |
| GET | `/api/snippets` | JWT or API Key | List all your snippets |
| GET | `/api/snippets/{id}` | JWT or API Key | Get one of your snippets by ID |
| PUT | `/api/snippets/{id}` | JWT or API Key | Update one of your snippets |
| DELETE | `/api/snippets/{id}` | JWT or API Key | Delete one of your snippets |
| GET | `/api/snippets/public` | None | Browse all public snippets (paginated) |
| GET | `/api/snippets/search` | None | Search public snippets by keyword + language |

### Query Parameters — Search

| Parameter | Required | Example | Description |
|-----------|----------|---------|-------------|
| `q` | No | `?q=bubble+sort` | Keyword matched against title and description |
| `language` | No | `?language=java` | Filter by programming language |
| `page` | No | `?page=0` | Page number, 0-based (default: 0) |
| `size` | No | `?size=20` | Results per page, max 50 (default: 20) |

---

## Example curl Commands

### Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "tushar",
    "email": "tushar@example.com",
    "password": "password123"
  }'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "apiKey": "a3f9c2d1e4b7083f..."
}
```

### Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "tushar", "password": "password123"}'
```

### Create a Snippet (JWT)

```bash
export TOKEN="eyJhbGciOiJIUzI1NiJ9..."

curl -X POST http://localhost:8080/api/snippets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Binary Search",
    "language": "java",
    "code": "int mid = left + (right - left) / 2;",
    "description": "Avoids integer overflow vs (left + right) / 2",
    "isPublic": true
  }'
```

### Create a Snippet (API Key)

```bash
curl -X POST http://localhost:8080/api/snippets \
  -H "X-API-Key: a3f9c2d1e4b7083f..." \
  -H "Content-Type: application/json" \
  -d '{
    "title": "FizzBuzz",
    "language": "python",
    "code": "print(\"Fizz\" * (i%3==0) + \"Buzz\" * (i%5==0) or i)",
    "isPublic": false
  }'
```

### Search Public Snippets

```bash
# By keyword and language
curl "http://localhost:8080/api/snippets/search?q=sort&language=java&page=0&size=10"

# By language only
curl "http://localhost:8080/api/snippets/search?language=python"
```

### Refresh Token

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "eyJhbGciOiJIUzI1NiJ9..."}'
```

### Trigger a Rate Limit (for testing)

```bash
# Run this 11 times quickly as an unauthenticated user
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "Request $i: HTTP %{http_code}\n" \
    http://localhost:8080/api/snippets/public
done
# Requests 1-10: HTTP 200
# Request 11:    HTTP 429
```

---

## Rate Limiting

| Caller type | Limit | Identifier | Redis key pattern |
|-------------|-------|------------|-------------------|
| Unauthenticated | 10 req/min | IP address | `rate_limit:ip:203.0.113.1` |
| Authenticated | 100 req/min | Username | `rate_limit:user:tushar` |

When the limit is exceeded the API responds with:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 42
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1718000000

{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Retry after 42 seconds.",
  "timestamp": "2025-06-10T14:30:00"
}
```

---

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DB_URL` | Yes | `jdbc:postgresql://localhost:5432/snippetvault` | PostgreSQL JDBC URL |
| `DB_USERNAME` | Yes | `postgres` | Database username |
| `DB_PASSWORD` | Yes | `postgres` | Database password |
| `REDIS_HOST` | Yes | `localhost` | Redis hostname |
| `REDIS_PORT` | Yes | `6379` | Redis port |
| `REDIS_PASSWORD` | No | *(empty)* | Redis AUTH password |
| `JWT_SECRET` | Yes | *(dev default)* | Base64-encoded secret, min 32 bytes. Generate: `openssl rand -base64 64` |
| `JWT_ACCESS_EXPIRY_MS` | No | `1800000` | Access token lifetime in ms (default 30 min) |
| `JWT_REFRESH_EXPIRY_MS` | No | `604800000` | Refresh token lifetime in ms (default 7 days) |
| `PORT` | No | `8080` | Server port — Railway injects this automatically |

---

## Project Structure

```
src/main/java/com/snippetvault/
├── config/          OpenAPI and Redis configuration beans
├── controller/      AuthController, SnippetController
├── dto/             Request and response records (Java 17)
├── exception/       Custom exceptions and GlobalExceptionHandler
├── model/           User and Snippet JPA entities
├── repository/      Spring Data JPA repositories
├── security/        JWT filter, API key filter, rate limit filter, SecurityConfig
├── service/         Business logic interfaces and implementations
└── util/            JwtUtil
```

---

## Self-Critique — What I Would Add Next

**True refresh token revocation** — the current implementation is stateless: issued refresh tokens are valid until they expire. A Redis blocklist storing invalidated token JTIs (JWT IDs) would allow logout and token revocation without breaking statelessness everywhere else.

**Flyway migrations** — `spring.jpa.hibernate.ddl-auto=update` is convenient for development but risky in production (it won't drop columns or handle complex schema changes). Flyway would version-control the schema with repeatable, audited SQL migration scripts.

**Full-text search** — the current `LIKE '%keyword%'` search cannot use a B-tree index and degrades at scale. PostgreSQL's `tsvector`/`tsquery` full-text search with a GIN index would handle large snippet tables efficiently.

**Pagination on `GET /api/snippets`** — the user's own snippets endpoint returns all results as a list. Adding pagination here would prevent issues for users with thousands of snippets.

**Rate limit Redis blocklist** — the fail-open approach (allow request when Redis is unreachable) is pragmatic but could be abused during an outage. A circuit breaker with a configurable fail-closed mode would be more robust for production.

**HTTPS enforcement** — currently handled by Railway's reverse proxy. If self-hosting, Spring Security's `requiresSecure()` or a redirect filter would enforce TLS at the application layer.

**Structured logging with correlation IDs** — injecting a `X-Request-Id` header into MDC (Mapped Diagnostic Context) would make log lines traceable across the filter chain, invaluable for debugging distributed issues.

---

## Running Tests

```bash
./mvnw test
```

37 tests across four classes: `JwtUtilTest`, `AuthServiceTest`, `SnippetServiceTest`, `RateLimitFilterTest`. All are pure unit tests using Mockito — no Spring context loaded, no database or Redis required.

---

## GitHub Topics

Add these to your repository for discoverability:

`spring-boot` `java` `rest-api` `jwt` `redis` `postgresql` `docker` `rate-limiting` `swagger` `portfolio`
