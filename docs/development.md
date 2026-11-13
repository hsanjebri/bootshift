# Local development

Phase 1 runs a **simulated** pipeline: runs move through every stage without cloning, building
or calling a model. It exists to prove the queue, the event log and the live dashboard.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 | Temurin recommended |
| Node.js | 24.22.3 or newer (or 22.22.3+) | Required by Angular 22 |
| Docker | any recent | For Postgres and for the backend's Testcontainers tests |

Maven is not needed: use `backend/mvnw`.

## Run it

```bash
# 1. Postgres
docker compose -f deploy/compose.dev.yaml up -d

# 2. Backend on http://127.0.0.1:8080
cd backend && ./mvnw spring-boot:run

# 3. Dashboard on http://localhost:4200 (proxies /api to the backend)
cd frontend && npm ci && npx ng serve
```

Open the dashboard, start a run, and watch it stream through every stage.

## Tests

```bash
cd backend && ./mvnw verify            # unit, ArchUnit and API tests (Testcontainers needs Docker)
cd frontend && npx ng test --watch=false
```

Without Docker, the backend API tests can use any Postgres instead of Testcontainers:

```bash
BOOTSHIFT_TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/bootshift_test ./mvnw verify
```

## API at a glance

| Method | Path | |
|---|---|---|
| `POST` | `/api/v1/runs` | `{"repository": "owner/name", "branch": "main"}`, optional `Idempotency-Key` header. Returns `202` |
| `GET` | `/api/v1/runs` | Newest first, `?limit=` up to 200 |
| `GET` | `/api/v1/runs/{id}` | |
| `POST` | `/api/v1/runs/{id}/cancel` | Queued runs stop at once; running ones at the next stage |
| `GET` | `/api/v1/runs/{id}/events` | Server-Sent Events; resumes from `Last-Event-ID` |

Errors use RFC 9457 Problem Details (`application/problem+json`).

## Observability (optional)

```bash
docker compose -f deploy/compose.dev.yaml --profile observability up -d
MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT=http://localhost:4318/v1/traces \
BOOTSHIFT_OTEL_METRICS_ENABLED=true ./mvnw spring-boot:run
```

Grafana is then on http://localhost:3000.

## Configuration

| Variable | Default | |
|---|---|---|
| `BOOTSHIFT_DB_URL` | `jdbc:postgresql://localhost:5432/bootshift` | |
| `BOOTSHIFT_DB_USER` / `BOOTSHIFT_DB_PASSWORD` | `bootshift` / `bootshift` | Local defaults only |
| `BOOTSHIFT_BIND_ADDRESS` | `127.0.0.1` | There is no login until Phase 6; keep it on localhost |
| `bootshift.runs.max-concurrent` | `2` | |
| `bootshift.pipeline.fake-step-delay` | `800ms` | Pause between simulated stages |
