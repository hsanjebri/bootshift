package dev.bootshift.run;

import java.util.UUID;

/** Domain errors raised by {@link RunService}. The web layer maps them to Problem Details. */
public final class RunExceptions {

    private RunExceptions() {
    }

    public static final class RunNotFound extends RuntimeException {
        public RunNotFound(UUID id) {
            super("No run with id " + id);
        }
    }

    public static final class RunAlreadyFinished extends RuntimeException {
        public RunAlreadyFinished(UUID id, RunState state) {
            super("Run " + id + " already finished as " + state);
        }
    }

    /** The same Idempotency-Key was sent with a different request body. */
    public static final class IdempotencyKeyReused extends RuntimeException {
        public IdempotencyKeyReused(String key) {
            super("Idempotency-Key '" + key + "' was already used for a different request");
        }
    }

    /** Thrown inside a pipeline when the run was cancelled; the worker turns it into CANCELLED. */
    public static final class RunCancelled extends RuntimeException {
        public RunCancelled(UUID id) {
            super("Run " + id + " was cancelled");
        }
    }
}
