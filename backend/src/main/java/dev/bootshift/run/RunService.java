package dev.bootshift.run;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.bootshift.repo.AllowlistPolicy;
import dev.bootshift.repo.RepositoryService;
import dev.bootshift.run.RunExceptions.IdempotencyKeyReused;
import dev.bootshift.run.RunExceptions.RepositoryNotAllowed;
import dev.bootshift.run.RunExceptions.RunAlreadyFinished;
import dev.bootshift.run.RunExceptions.RunNotFound;

/** Entry point for starting, reading and cancelling runs. Shared by the REST API and (later) MCP. */
@Service
public class RunService {

    private final RunStore store;
    private final RunStateWriter writer;
    private final RunEventLog events;
    private final RepositoryService repositories;
    private final AllowlistPolicy allowlist;

    RunService(RunStore store, RunStateWriter writer, RunEventLog events, RepositoryService repositories,
               AllowlistPolicy allowlist) {
        this.store = store;
        this.writer = writer;
        this.events = events;
        this.repositories = repositories;
        this.allowlist = allowlist;
    }

    /** Result of {@link #start}: {@code created} is false when an idempotent retry returned an existing run. */
    public record Started(Run run, boolean created) {
    }

    @Transactional
    public Started start(StartRun command, @Nullable String idempotencyKey) {
        if (idempotencyKey != null) {
            var existing = store.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return replay(existing.get(), command, idempotencyKey);
            }
        }
        if (allowlist.enforced() && !repositories.isAllowed(command.repository())) {
            throw new RepositoryNotAllowed(command.repository().toString());
        }
        UUID repositoryId = repositories.findOrCreate(command.repository());
        var inserted = store.insertQueued(repositoryId, command.branch(), idempotencyKey);
        if (inserted.isEmpty()) {
            // Lost a race with a concurrent request carrying the same key.
            Run winner = store.findByIdempotencyKey(Objects.requireNonNull(idempotencyKey)).orElseThrow();
            return replay(winner, command, idempotencyKey);
        }
        UUID id = inserted.get();
        events.append(id, RunEvent.STATE_CHANGED, stateChange(null, RunState.QUEUED));
        return new Started(get(id), true);
    }

    public Run get(UUID id) {
        return store.findById(id).orElseThrow(() -> new RunNotFound(id));
    }

    public List<Run> list(int limit) {
        return store.list(Math.clamp(limit, 1, 200));
    }

    public List<RunEvent> events(UUID id, long afterEventId, int limit) {
        return events.after(id, afterEventId, limit);
    }

    /**
     * Cancels a run. A queued run is cancelled at once; a running one is flagged and the worker
     * stops it at the next stage boundary.
     */
    @Transactional
    public Run cancel(UUID id) {
        Run run = get(id);
        if (run.status().isTerminal()) {
            throw new RunAlreadyFinished(id, run.status());
        }
        if (run.status() == RunState.QUEUED && writer.move(id, RunState.QUEUED, RunState.CANCELLED, null)) {
            return get(id);
        }
        if (!store.requestCancel(id)) {
            throw new RunAlreadyFinished(id, get(id).status());
        }
        events.append(id, RunEvent.CANCEL_REQUESTED, Map.of());
        return get(id);
    }

    private Started replay(Run existing, StartRun command, String key) {
        boolean sameRequest = existing.repository().equals(command.repository().toString())
                && Objects.equals(existing.branch(), command.branch());
        if (!sameRequest) {
            throw new IdempotencyKeyReused(key);
        }
        return new Started(existing, false);
    }

    static Map<String, @Nullable Object> stateChange(@Nullable RunState from, RunState to) {
        Map<String, @Nullable Object> payload = new HashMap<>();
        payload.put("from", from == null ? null : from.name());
        payload.put("to", to.name());
        return payload;
    }
}
