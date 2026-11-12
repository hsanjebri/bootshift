package dev.bootshift.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** JDBC access to the {@code run} table. Every status change is guarded by the expected current status. */
@Repository
class RunStore {

    private static final String SELECT = """
            SELECT r.id, repo.owner || '/' || repo.name AS repository, r.status, r.stage, r.branch, r.pr_url,
                   r.is_draft, r.failure_reason, r.cancel_requested, r.created_at, r.started_at, r.finished_at
            FROM run r JOIN repository repo ON repo.id = r.repository_id
            """;

    private static final String[] TERMINAL = Arrays.stream(RunState.values())
            .filter(RunState::isTerminal).map(Enum::name).toArray(String[]::new);

    private final JdbcClient jdbc;

    RunStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a QUEUED run. Returns empty if a run with the same idempotency key already exists. */
    Optional<UUID> insertQueued(UUID repositoryId, @Nullable String branch, @Nullable String idempotencyKey) {
        UUID id = UUID.randomUUID();
        return jdbc.sql("""
                INSERT INTO run (id, repository_id, status, branch, idempotency_key)
                VALUES (:id, :repositoryId, 'QUEUED', :branch, :key)
                ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                RETURNING id
                """)
                .param("id", id)
                .param("repositoryId", repositoryId)
                .param("branch", branch)
                .param("key", idempotencyKey)
                .query(UUID.class)
                .optional();
    }

    Optional<Run> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE r.id = :id").param("id", id).query(this::map).optional();
    }

    Optional<Run> findByIdempotencyKey(String key) {
        return jdbc.sql(SELECT + " WHERE r.idempotency_key = :key").param("key", key).query(this::map).optional();
    }

    List<Run> list(int limit) {
        return jdbc.sql(SELECT + " ORDER BY r.created_at DESC LIMIT :limit").param("limit", limit).query(this::map).list();
    }

    /**
     * Atomically claims the oldest QUEUED run and moves it to CLONING. {@code SKIP LOCKED} lets
     * several workers poll at once without blocking each other or claiming the same run.
     */
    Optional<UUID> claimNext() {
        return jdbc.sql("""
                UPDATE run SET status = 'CLONING', stage = 'CLONING', started_at = coalesce(started_at, now())
                WHERE id = (
                    SELECT id FROM run
                    WHERE status = 'QUEUED' AND NOT cancel_requested
                    ORDER BY created_at
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED)
                RETURNING id
                """)
                .query(UUID.class)
                .optional();
    }

    /** Moves the run from {@code from} to {@code to}. Returns false if the run was no longer in {@code from}. */
    boolean transition(UUID id, RunState from, RunState to, @Nullable String failureReason) {
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException("Illegal run transition " + from + " -> " + to);
        }
        return jdbc.sql("""
                UPDATE run
                SET status = :to,
                    stage = CASE WHEN :terminal THEN stage ELSE :to END,
                    failure_reason = coalesce(:reason, failure_reason),
                    is_draft = :draft,
                    finished_at = CASE WHEN :terminal THEN now() ELSE finished_at END
                WHERE id = :id AND status = :from
                """)
                .param("id", id)
                .param("from", from.name())
                .param("to", to.name())
                .param("draft", to == RunState.DRAFT)
                .param("terminal", to.isTerminal())
                .param("reason", failureReason)
                .update() == 1;
    }

    /** Flags a non-terminal run for cancellation. Returns false if it is already terminal. */
    boolean requestCancel(UUID id) {
        return jdbc.sql("UPDATE run SET cancel_requested = TRUE WHERE id = :id AND status NOT IN (:terminal)")
                .param("id", id)
                .param("terminal", List.of(TERMINAL))
                .update() == 1;
    }

    boolean isCancelRequested(UUID id) {
        return jdbc.sql("SELECT cancel_requested FROM run WHERE id = :id").param("id", id)
                .query(Boolean.class).optional().orElse(false);
    }

    /**
     * After a restart, puts runs that were in progress back on the queue. Phase 1 restarts them
     * from the beginning; resuming from the last completed stage comes with the real pipeline.
     */
    List<UUID> requeueInFlight() {
        return jdbc.sql("""
                UPDATE run SET status = 'QUEUED'
                WHERE status NOT IN (:terminal) AND status <> 'QUEUED'
                RETURNING id
                """)
                .param("terminal", List.of(TERMINAL))
                .query(UUID.class)
                .list();
    }

    private Run map(ResultSet rs, int row) throws SQLException {
        String stage = rs.getString("stage");
        return new Run(
                rs.getObject("id", UUID.class),
                rs.getString("repository"),
                RunState.valueOf(rs.getString("status")),
                stage == null ? null : RunState.valueOf(stage),
                rs.getString("branch"),
                rs.getString("pr_url"),
                rs.getBoolean("is_draft"),
                rs.getString("failure_reason"),
                rs.getBoolean("cancel_requested"),
                instant(rs.getTimestamp("created_at")),
                nullableInstant(rs.getTimestamp("started_at")),
                nullableInstant(rs.getTimestamp("finished_at")));
    }

    private static Instant instant(Timestamp ts) {
        return ts.toInstant();
    }

    private static @Nullable Instant nullableInstant(@Nullable Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
