package dev.bootshift.run;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/** One entry in a run's event log. {@code id} is monotonic across all runs. */
public record RunEvent(long id, UUID runId, String type, JsonNode payload, Instant createdAt) {

    /** Event types written by the run lifecycle. Pipelines may add their own. */
    public static final String STATE_CHANGED = "STATE_CHANGED";
    public static final String LOG = "LOG";
    public static final String CANCEL_REQUESTED = "CANCEL_REQUESTED";
    public static final String RECOVERED = "RECOVERED";
    /** A sandbox build finished; the payload summarizes its {@code build_result}. */
    public static final String BUILD_FINISHED = "BUILD_FINISHED";

    /** True for the event that moves a run into a terminal state; nothing follows it. */
    public boolean isFinal() {
        if (!STATE_CHANGED.equals(type)) {
            return false;
        }
        JsonNode to = payload.get("to");
        return to != null && to.isString() && RunState.valueOf(to.asString()).isTerminal();
    }
}
