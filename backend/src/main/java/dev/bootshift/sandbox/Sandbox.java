package dev.bootshift.sandbox;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runs one command in a fresh container that sees only a run's working copy and Maven cache.
 * Implementations must enforce every limit in {@link SandboxProperties}; callers cannot loosen them.
 */
public interface Sandbox {

    /**
     * Runs the command and blocks until it exits, times out or is cancelled. The container is
     * removed afterwards in every case, including when the thread is interrupted.
     *
     * @param lines     receives each output line as it arrives
     * @param cancelled polled while the build runs; returning true kills the container
     */
    Result run(Command command, Consumer<String> lines, BooleanSupplier cancelled) throws InterruptedException;

    /** Removes the run's Maven cache. Call when the run ends. */
    void release(UUID runId);

    /**
     * @param runId     owner of the working copy and the Maven cache
     * @param name      short label for the container name, for example {@code baseline}
     * @param workspace the working copy, mounted read-write at {@code /workspace}
     * @param jdk       major Java version; the image provides 17, 21 and 25
     * @param argv      the command, for example {@code mvn -B verify}
     * @param timeout   killed after this; capped at {@link SandboxProperties#buildTimeout()}
     * @param logFile   where the full output is written, outside the working copy
     */
    record Command(UUID runId, String name, Path workspace, int jdk, List<String> argv, Duration timeout, Path logFile) {

        public static final List<Integer> SUPPORTED_JDKS = List.of(17, 21, 25);

        public Command {
            if (!SUPPORTED_JDKS.contains(jdk)) {
                throw new IllegalArgumentException("Unsupported JDK " + jdk + "; the sandbox has " + SUPPORTED_JDKS);
            }
            if (!name.matches("[a-z0-9-]{1,30}")) {
                throw new IllegalArgumentException("Invalid sandbox command name: " + name);
            }
            argv = List.copyOf(argv);
            if (argv.isEmpty()) {
                throw new IllegalArgumentException("Empty command");
            }
        }
    }

    enum Outcome { EXITED, TIMED_OUT, CANCELLED }

    /**
     * @param exitCode     process exit code; meaningless unless {@code outcome} is {@code EXITED}
     * @param logTruncated true if output went past {@link SandboxProperties#maxLogSize()}
     */
    record Result(Outcome outcome, int exitCode, Duration duration, Path logFile, boolean logTruncated) {

        public boolean succeeded() {
            return outcome == Outcome.EXITED && exitCode == 0;
        }
    }

    /** Docker, the image or the network is missing or misconfigured. Not the project's fault. */
    final class SandboxUnavailable extends RuntimeException {
        public SandboxUnavailable(String message, Throwable cause) {
            super(message, cause);
        }

        public SandboxUnavailable(String message) {
            super(message);
        }
    }
}
