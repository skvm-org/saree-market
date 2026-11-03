# Backend logging plan

Status: proposed, not started.

## Why

When something fails in production today, the only evidence is whatever Spring Boot prints by default. Surveyed on `main` (after PR #4):

- **No logging config at all.** No `logging:` block in `application.yml`, no `logback-spring.xml`. Plain-text console output at INFO, no request id, no user id.
- **About 12 log statements, none on the request path.** They are all in background jobs (`OrderExpiryJob`, `HoldService`, `NotificationService`), image processing and `FallbackImageStorage`. Nothing records a request, a login, an order or an upload.
- **`ApiExceptionHandler` logs nothing and has no catch-all.** An unexpected exception falls through to Spring's default `/error` body, which is not our `{code, message, fields}` shape, and nothing ties the response to a log line.
- **S3 failures are only visible if they throw.** `S3ImageStorage` has no logging of its own; `FallbackImageStorage` logs a read fallback at WARN with no key counts.
- **No log rotation on EC2.** `deploy/docker-compose.yml` sets no logging options, so Docker's default `json-file` driver grows without limit on the instance disk. Logs are only reachable through `docker compose logs` over SSH.
- **`/api/v1/health` does not touch the database**, so "UP" does not prove the app can serve.

## Goals

1. Any failed request can be traced from the client's error to the exact log lines, with a request id.
2. A 500 always leaves a stack trace in the logs and an `ApiError` body for the client.
3. Key business events (login, order, payment, upload, S3 failure) are searchable.
4. Logs cannot fill the disk, and survive a container redeploy.
5. No secrets or personal data in logs.

## Phase A: application logging (one PR, backend only)

1. **Structured output in production.** Spring Boot has built-in structured logging, so no new dependency: set `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` in `deploy/docker-compose.yml`. Local keeps the readable text format. Add `logging.level` defaults in `application.yml` (`com.sareemarket: INFO`, `org.hibernate.SQL` off).
2. **Request log filter** (`common/RequestLogFilter`, runs first, before Spring Security so 401 and 403 are included). One INFO line per request: method, path (no query string), status, duration in ms, user id and role if known, client IP from `X-Forwarded-For`, request id. Skip `/api/v1/health`. 5xx at ERROR, 4xx at WARN only for 401, 403 and 429, others INFO.
3. **Request id.** Take the incoming `X-Request-Id` if present, else generate one. Put it in the MDC (`requestId`, `userId`), echo it as an `X-Request-Id` response header, and add a `requestId` field to `ApiError`. Clear the MDC in `finally`. Android and iOS can later show "Reference: abc123" on error screens; not part of this PR.
4. **Exception handler.**
   - Add `@ExceptionHandler(Exception.class)`: log at ERROR with the stack trace, return `500 {code: "INTERNAL_ERROR", message: "Something went wrong", requestId}` and never the exception text.
   - Log `ApiException` at WARN for 401, 403, 409 and 5xx; INFO for the rest, code and status only, no stack.
   - Log validation failures at INFO with field names only, never values.
5. **Auth events** (`AuthService`, `LoginAttemptLimiter`, `SecurityConfig`): login success and failure, lockout start, registration, disabled-account rejection. Phone numbers masked to the last four digits.
6. **Business events at INFO**, one line each, with ids only: collection created (seller, photo count), order placed, order status changed (from, to, actor), payment submitted and proof stored, order confirmed or rejected, hold and order expiry job results (counts), notification delivery counts.
7. **Storage.** `S3ImageStorage`: log put, get and delete failures at ERROR with the key and the AWS error code; successful puts at DEBUG. `FallbackImageStorage`: keep the WARN, include a count so a surge of fallbacks is visible.
8. **Startup banner line** (INFO): active profile, storage type and bucket, Flyway version, build SHA (`BUILD_SHA` env set by the deploy workflow). This answers "is S3 on?" without guessing.

Never log: passwords, JWTs, the `Authorization` header, request bodies, full phone numbers, addresses, UPI IDs, full UTRs (mask to the last four).

### Tests (Phase A)
- A 500 returns an `ApiError` with `requestId`, no stack trace in the body, and one ERROR log line with the stack trace.
- The request filter logs method, path, status and duration, and clears the MDC afterwards.
- `X-Request-Id` is echoed, and a client-supplied one is reused.
- A failed login logs a masked phone and never contains the password (`OutputCaptureExtension`).
- The app boots with the `logstash` format and the output parses as JSON.

## Phase B: running it on EC2 (compose and docs, no code)

1. **Rotation.** In `deploy/docker-compose.yml` give `backend` and `caddy` `logging: { driver: json-file, options: { max-size: "10m", max-file: "5" } }`. This caps each at 50 MB.
2. **Runbook** (`docs/OPERATIONS.md`): how to read logs, e.g. `docker compose logs --since 1h backend | jq 'select(.level=="ERROR")'`, how to find a request by id, what each level means.
3. **Caddy access log** at INFO so 502s (backend down) are visible even when the app logs nothing.

## Phase C: stays useful over time (optional, one PR each)

1. **CloudWatch Logs** through Docker's `awslogs` driver, so logs survive the instance and are searchable without SSH. Needs `logs:CreateLogStream` and `logs:PutLogEvents` added to the instance role (`deploy/s3-iam-policy.json` becomes a general policy). Set retention to 30 days. Add one metric filter on ERROR with an alarm to email you. This is an AWS-managed service, not an app dependency, but it is the one outside piece in this plan, so it is your call.
2. **Health that checks the database.** Make `/api/v1/health` run a `SELECT 1` and report `DOWN` with 503 on failure; the deploy script already waits on it.
3. **Slow request and query warnings** above one second.
4. **Error tracking such as Sentry**: not recommended now (external service; the MVP brief says no external services).

## Sequence and effort

| PR | Content | Effort |
| --- | --- | --- |
| 1 | Phase A with tests | about a day |
| 2 | Phase B | an hour |
| 3 | Phase C items you want | half a day each |

Each PR follows the usual slice rules: `./mvnw verify` must pass, deploys on merge to `main`.

## Decisions for you

- Structured JSON in production (recommended) or keep plain text?
- Is CloudWatch (Phase C.1) wanted, or is `docker compose logs` over SSH enough for now?
- Should the Android and iOS apps show the request id on error screens (small follow-up)?
