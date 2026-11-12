package dev.bootshift.run;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.json.JsonMapper;

/** Append-only event log per run, stored in {@code run_event}. */
@Component
public class RunEventLog {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final RunEventSignal signal;

    public RunEventLog(JdbcClient jdbc, JsonMapper json, RunEventSignal signal) {
        this.jdbc = jdbc;
        this.json = json;
        this.signal = signal;
    }

    public void append(UUID runId, String type, Map<String, ?> payload) {
        jdbc.sql("INSERT INTO run_event (run_id, type, payload) VALUES (:runId, :type, CAST(:payload AS jsonb))")
                .param("runId", runId)
                .param("type", type)
                .param("payload", json.writeValueAsString(payload))
                .update();
        // Wake streams only once the row is visible to them.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    signal.fire();
                }
            });
        } else {
            signal.fire();
        }
    }

    /** Events of one run with an id greater than {@code afterId}, oldest first. */
    public List<RunEvent> after(UUID runId, long afterId, int limit) {
        return jdbc.sql("""
                SELECT id, run_id, type, payload::text AS payload, created_at
                FROM run_event
                WHERE run_id = :runId AND id > :afterId
                ORDER BY id
                LIMIT :limit
                """)
                .param("runId", runId)
                .param("afterId", afterId)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    private RunEvent map(ResultSet rs, int row) throws SQLException {
        return new RunEvent(
                rs.getLong("id"),
                rs.getObject("run_id", UUID.class),
                rs.getString("type"),
                json.readTree(rs.getString("payload")),
                rs.getTimestamp("created_at").toInstant());
    }
}
