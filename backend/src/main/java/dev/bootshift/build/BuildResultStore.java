package dev.bootshift.build;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC access to {@code build_result} and {@code failure}. */
@Repository
public class BuildResultStore {

    private final JdbcClient jdbc;

    BuildResultStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A stored build as the API shows it. The server-side log path is not included. */
    public record StoredBuild(
            UUID id,
            BuildKind kind,
            int iteration,
            String outcome,
            @Nullable Integer exitCode,
            int jdk,
            int testsRun,
            int testsFailed,
            int testsSkipped,
            long durationMs,
            boolean logTruncated,
            Instant createdAt,
            List<StoredFailure> failures) {
    }

    public record StoredFailure(String fingerprint, FailureType type, String module, @Nullable String file,
                                @Nullable Integer line, @Nullable String symbol, String message, boolean isNew) {
    }

    @Transactional
    public UUID save(UUID runId, int iteration, BuildReport report) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO build_result (id, run_id, kind, iteration, outcome, exit_code, jdk, tests_run, tests_failed,
                                          tests_skipped, duration_ms, log_path, log_truncated)
                VALUES (:id, :runId, :kind, :iteration, :outcome, :exitCode, :jdk, :run, :failed, :skipped, :duration, :log, :truncated)
                """)
                .param("id", id)
                .param("runId", runId)
                .param("kind", report.kind().name())
                .param("iteration", iteration)
                .param("outcome", report.outcome().name())
                .param("exitCode", report.outcome() == dev.bootshift.sandbox.Sandbox.Outcome.EXITED ? report.exitCode() : null)
                .param("jdk", report.jdk())
                .param("run", report.testsRun())
                .param("failed", report.testsFailed())
                .param("skipped", report.testsSkipped())
                .param("duration", report.duration().toMillis())
                .param("log", report.logFile().toString())
                .param("truncated", report.logTruncated())
                .update();
        for (Failure failure : report.failures()) {
            jdbc.sql("""
                    INSERT INTO failure (id, build_result_id, fingerprint, type, module, file, line, symbol, message)
                    VALUES (:id, :build, :fingerprint, :type, :module, :file, :line, :symbol, :message)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("build", id)
                    .param("fingerprint", failure.fingerprint())
                    .param("type", failure.type().name())
                    .param("module", failure.module())
                    .param("file", failure.file())
                    .param("line", failure.line())
                    .param("symbol", failure.symbol())
                    .param("message", failure.message())
                    .update();
        }
        return id;
    }

    /** Every build of a run, oldest first, with its failures. */
    public List<StoredBuild> list(UUID runId) {
        Map<UUID, List<StoredFailure>> failures = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT f.build_result_id, f.fingerprint, f.type, f.module, f.file, f.line, f.symbol, f.message, f.is_new
                FROM failure f JOIN build_result b ON b.id = f.build_result_id
                WHERE b.run_id = :runId
                ORDER BY f.type, f.module, f.file, f.line
                """)
                .param("runId", runId)
                .query((rs, n) -> {
                    failures.computeIfAbsent(rs.getObject("build_result_id", UUID.class), k -> new java.util.ArrayList<>())
                            .add(mapFailure(rs));
                    return null;
                })
                .list();
        return jdbc.sql("SELECT * FROM build_result WHERE run_id = :runId ORDER BY created_at, id")
                .param("runId", runId)
                .query((rs, n) -> mapBuild(rs, failures))
                .list();
    }

    private static StoredBuild mapBuild(ResultSet rs, Map<UUID, List<StoredFailure>> failures) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new StoredBuild(
                id,
                BuildKind.valueOf(rs.getString("kind")),
                rs.getInt("iteration"),
                rs.getString("outcome"),
                rs.getObject("exit_code", Integer.class),
                rs.getInt("jdk"),
                rs.getInt("tests_run"),
                rs.getInt("tests_failed"),
                rs.getInt("tests_skipped"),
                rs.getLong("duration_ms"),
                rs.getBoolean("log_truncated"),
                rs.getTimestamp("created_at").toInstant(),
                List.copyOf(failures.getOrDefault(id, List.of())));
    }

    private static StoredFailure mapFailure(ResultSet rs) throws SQLException {
        return new StoredFailure(
                rs.getString("fingerprint"),
                FailureType.valueOf(rs.getString("type")),
                rs.getString("module"),
                rs.getString("file"),
                rs.getObject("line", Integer.class),
                rs.getString("symbol"),
                rs.getString("message"),
                rs.getBoolean("is_new"));
    }
}
