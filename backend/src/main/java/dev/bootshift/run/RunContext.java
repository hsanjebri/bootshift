package dev.bootshift.run;

import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.bootshift.run.RunExceptions.RunCancelled;

/** What a {@link MigrationPipeline} may do to the run it is executing. */
public final class RunContext {

    private final UUID runId;
    private final String repository;
    private final @Nullable String branch;
    private final RunStore store;
    private final RunStateWriter writer;
    private final RunEventLog events;
    private RunState state;

    RunContext(UUID runId, String repository, @Nullable String branch, RunState state, RunStore store, RunStateWriter writer,
               RunEventLog events) {
        this.runId = runId;
        this.repository = repository;
        this.branch = branch;
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

    /** The base branch the run was started with, or null for the repository's default branch. */
    public @Nullable String branch() {
        return branch;
    }

    public void log(String message) {
        events.append(runId, RunEvent.LOG, Map.of("message", message));
    }

    /** Appends a pipeline-specific event, for example {@link RunEvent#BUILD_FINISHED}. */
    public void event(String type, Map<String, ?> payload) {
        events.append(runId, type, payload);
    }

    /** Ends the run in a terminal state with a reason shown to the user, such as {@code NOT_ELIGIBLE}. */
    public void stop(RunState terminal, String reason) {
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException(terminal + " is not a terminal state");
        }
        checkCancelled();
        move(terminal, reason);
    }

    /** Records the commit the run started from and the JDK its builds use. */
    public void recordDetails(@Nullable String sourceSha, @Nullable Integer jdk) {
        store.recordDetails(runId, sourceSha, jdk);
    }

    public boolean isCancelRequested() {
        return store.isCancelRequested(runId);
    }

    /** Throws {@link RunCancelled} if a cancel was requested. Call it at safe points. */
    public void checkCancelled() {
        if (isCancelRequested()) {
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
