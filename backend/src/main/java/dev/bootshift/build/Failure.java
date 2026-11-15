package dev.bootshift.build;

import org.jspecify.annotations.Nullable;

/**
 * One failure found in a build.
 *
 * @param module      Maven module directory relative to the repository root, {@code .} for the root
 * @param file        source path relative to the repository root, or the test class for test failures
 * @param line        line number, if known; not part of the fingerprint
 * @param symbol      what failed: the missing symbol, the test method, or the unresolved artifact
 * @param message     first line of the error, with run-specific details removed
 * @param fingerprint stable identity across builds, see {@link Fingerprints}
 */
public record Failure(
        FailureType type,
        String module,
        @Nullable String file,
        @Nullable Integer line,
        @Nullable String symbol,
        String message,
        String fingerprint) {

    static Failure of(FailureType type, String module, @Nullable String file, @Nullable Integer line,
                      @Nullable String symbol, String rawMessage) {
        String message = Fingerprints.normalize(rawMessage);
        return new Failure(type, module, file, line, symbol, message,
                Fingerprints.of(type, module, file, symbol, message));
    }
}
