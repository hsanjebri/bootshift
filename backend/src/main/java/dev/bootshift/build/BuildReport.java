package dev.bootshift.build;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import dev.bootshift.sandbox.Sandbox.Outcome;

/**
 * The result of one Maven build in the sandbox.
 *
 * @param jdk         major Java version the build ran with
 * @param testsFailed failures plus errors
 * @param failures    compiler, dependency, test and other failures, each with a fingerprint
 */
public record BuildReport(
        BuildKind kind,
        int jdk,
        Outcome outcome,
        int exitCode,
        Duration duration,
        int testsRun,
        int testsFailed,
        int testsSkipped,
        List<Failure> failures,
        Path logFile,
        boolean logTruncated) {

    public BuildReport {
        failures = List.copyOf(failures);
    }

    /** False if the build timed out, was cancelled, or has compiler or dependency errors. */
    public boolean compiles() {
        return outcome == Outcome.EXITED && failures.stream()
                .noneMatch(f -> f.type() == FailureType.COMPILE || f.type() == FailureType.DEPENDENCY);
    }

    public long count(FailureType type) {
        return failures.stream().filter(f -> f.type() == type).count();
    }
}
