# Local development

The backend has two pipeline modes:

- **`fake`** (the default) simulates every stage. It needs no Docker, GitHub App or model, and
  exists to work on the queue, the event log and the dashboard.
- **`real`** clones the repository and runs the **baseline build in the sandbox** (Phase 2). The
  stages after the baseline are still simulated, and every log line for them starts with
  `[simulated]`.

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 25 | Temurin recommended |
| Node.js | 24.22.3 or newer (or 22.22.3+) | Required by Angular 22 |
| Docker | any recent | For Postgres, the sandbox and the backend's Testcontainers tests |

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

## Run real baselines (Phase 2)

```bash
# 1. The Maven proxy and the internal sandbox network
docker compose -f deploy/compose.dev.yaml up -d reposilite

# 2. The sandbox image: Temurin 17, 21 and 25 with Maven 3.9.16
docker build -t bootshift-sandbox:dev sandbox

# 3. The backend in real mode
cd backend && BOOTSHIFT_PIPELINE_MODE=real ./mvnw spring-boot:run
```

Without a GitHub App, only public repositories can be cloned. Each build runs in a new container
that:

- runs as uid 1000 with no capabilities, `no-new-privileges` and a read-only root;
- sees only the run's working copy (`/workspace`), its own Maven cache volume (`/m2`) and a tmpfs;
- has 4 GB of memory, 2 CPUs, 512 processes and 20 minutes;
- is on the internal `bootshift-sandbox` network, where the only way out is the Reposilite proxy
  to Maven Central;
- gets an environment built from scratch, with no tokens, keys or database settings.

Bootshift checks that the network is internal before every build and refuses to build otherwise.
The working copy is deleted when the run ends. Build logs stay in
`${java.io.tmpdir}/bootshift/logs/<run>/` (set `BOOTSHIFT_WORKSPACE` to move them).

The baseline runs `mvn -B -fae -Dmaven.test.failure.ignore=true clean verify`, so one build reports
every failing test. Compiler and dependency errors stop the run as `NOT_ELIGIBLE`; failing tests are
recorded and allowed. Tests that need Docker (Testcontainers) cannot run in the sandbox and show up
as failures until Phase 3 reports them as "Not verified".

## Tests

```bash
cd backend && ./mvnw verify            # unit, ArchUnit, sandbox and API tests (needs Docker)
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
| `GET` | `/api/v1/runs/{id}/builds` | Sandbox builds with test counts and fingerprinted failures |

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
| `BOOTSHIFT_PIPELINE_MODE` | `fake` | `real` clones and runs the baseline in the sandbox |
| `BOOTSHIFT_WORKSPACE` | `${java.io.tmpdir}/bootshift` | Working copies and build logs. Keep it out of synced folders |
| `BOOTSHIFT_SANDBOX_IMAGE` | `bootshift-sandbox:dev` | |
| `bootshift.sandbox.*` | see `application.yaml` | Memory, CPUs, process limit, timeout, network, proxy URL |
