package dev.bootshift.run;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.bootshift.run.RunExceptions.RunCancelled;

/** What a {@link MigrationPipeline} may do to the run it is executing. */
public final class RunContext {

    private final UUID runId;
    private final String repository;
    private final RunStore store;
    private final RunStateWriter writer;
    private final RunEventLog events;
    private RunState state;

    RunContext(UUID runId, String repository, RunState state, RunStore store, RunStateWriter writer, RunEventLog events) {
        this.runId = runId;
        this.repository = repository;
        this.state = state;
        this.store = store;
        this.writer = writer;
        this.events = events;
    }

    public UUID runId() {
        return runId;
    }

    public String repository() {
        return repository;
    }

    public RunState state() {
        return state;
    }

    /** Moves the run to the next state. Checks for cancellation first. */
    public void advance(RunState next) {
        checkCancelled();
        move(next, null);
    }

    public void log(String message) {
        events.append(runId, RunEvent.LOG, Map.of("message", message));
    }

    /** Throws {@link RunCancelled} if a cancel was requested. Call it at safe points. */
    public void checkCancelled() {
        if (store.isCancelRequested(runId)) {
            throw new RunCancelled(runId);
        }
    }

    void move(RunState next, @Nullable String reason) {
        if (!writer.move(runId, state, next, reason)) {
            throw new IllegalStateException("Run " + runId + " was modified concurrently; expected " + state);
        }
        state = next;
    }
}
