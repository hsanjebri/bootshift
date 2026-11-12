-- Phase 1 schema: repositories, runs and the run event log (README section 9).
-- Later phases add build_result, failure, iteration, tool_call, change, guard_finding and api_key.

CREATE TABLE repository (
    id             UUID PRIMARY KEY,
    provider       TEXT        NOT NULL DEFAULT 'github',
    owner          TEXT        NOT NULL,
    name           TEXT        NOT NULL,
    default_branch TEXT,
    allowed        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT repository_unique_name UNIQUE (provider, owner, name)
);

CREATE TABLE run (
    id               UUID PRIMARY KEY,
    repository_id    UUID        NOT NULL REFERENCES repository (id),
    status           TEXT        NOT NULL,
    -- The last non-terminal stage the run reached; tells you where a FAILED or CANCELLED run stopped.
    stage            TEXT,
    source_sha       TEXT,
    branch           TEXT,
    pr_url           TEXT,
    is_draft         BOOLEAN     NOT NULL DEFAULT FALSE,
    from_version     TEXT,
    to_version       TEXT,
    jdk              TEXT,
    model            TEXT,
    prompt_version   TEXT,
    recipe_versions  TEXT,
    tokens_in        BIGINT      NOT NULL DEFAULT 0,
    tokens_out       BIGINT      NOT NULL DEFAULT 0,
    cost_estimate    NUMERIC(12, 4),
    failure_reason   TEXT,
    cancel_requested BOOLEAN     NOT NULL DEFAULT FALSE,
    idempotency_key  TEXT,
    created_by       TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ
);

-- The queue: workers claim the oldest QUEUED run with FOR UPDATE SKIP LOCKED.
CREATE INDEX run_queued_idx ON run (created_at) WHERE status = 'QUEUED';
CREATE INDEX run_created_idx ON run (created_at DESC);
CREATE UNIQUE INDEX run_idempotency_key_idx ON run (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- Backs both the live SSE stream and replay after a reload (Last-Event-ID).
CREATE TABLE run_event (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id     UUID        NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    type       TEXT        NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX run_event_run_idx ON run_event (run_id, id);
