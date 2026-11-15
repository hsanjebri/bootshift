-- Phase 2: build results and the failures found in them (README section 9).

CREATE TABLE build_result (
    id            UUID PRIMARY KEY,
    run_id        UUID        NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    kind          TEXT        NOT NULL,
    iteration     INT         NOT NULL DEFAULT 0,
    -- EXITED, TIMED_OUT or CANCELLED. exit_code is only meaningful for EXITED.
    outcome       TEXT        NOT NULL,
    exit_code     INT,
    jdk           INT         NOT NULL,
    tests_run     INT         NOT NULL DEFAULT 0,
    tests_failed  INT         NOT NULL DEFAULT 0,
    tests_skipped INT         NOT NULL DEFAULT 0,
    duration_ms   BIGINT      NOT NULL,
    log_path      TEXT,
    log_truncated BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX build_result_run_idx ON build_result (run_id, created_at);

CREATE TABLE failure (
    id              UUID PRIMARY KEY,
    build_result_id UUID    NOT NULL REFERENCES build_result (id) ON DELETE CASCADE,
    fingerprint     TEXT    NOT NULL,
    type            TEXT    NOT NULL,
    module          TEXT    NOT NULL,
    file            TEXT,
    line            INT,
    -- Not in the README data model: the missing symbol, failing test method or unresolved artifact.
    -- Part of the fingerprint, so stored to explain it.
    symbol          TEXT,
    message         TEXT    NOT NULL,
    -- Set by the baseline comparison (Phase 3): true if the baseline did not have this fingerprint.
    is_new          BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX failure_build_idx ON failure (build_result_id);
