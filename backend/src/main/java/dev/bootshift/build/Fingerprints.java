package dev.bootshift.build;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Failure fingerprints (README section 9): a hash of type, module, file, symbol and normalized
 * message. Line numbers, absolute paths, ports, durations and object hashes are left out, so the
 * same problem has the same fingerprint in the baseline, after recipes and after each repair.
 */
public final class Fingerprints {

    static final int MAX_MESSAGE = 300;

    private static final Pattern WORKSPACE = Pattern.compile("/workspace/");
    private static final Pattern UUID = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern IDENTITY_HASH = Pattern.compile("@[0-9a-f]{5,}\\b");
    private static final Pattern TIMESTAMP = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern DURATION = Pattern.compile("\\b\\d+(\\.\\d+)? ?(ns|ms|s|sec|seconds)\\b");
    private static final Pattern PORT = Pattern.compile("\\b(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0):\\d{2,5}\\b");
    private static final Pattern LINE_REF = Pattern.compile("(\\.java|\\.kt):\\[?\\d+(,\\d+)?]?");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Fingerprints() {
    }

    /** First line of the message, without details that change from run to run. */
    public static String normalize(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "(no message)";
        }
        String first = message.strip().lines().findFirst().orElse("");
        String clean = WORKSPACE.matcher(first).replaceAll("");
        clean = UUID.matcher(clean).replaceAll("<uuid>");
        clean = TIMESTAMP.matcher(clean).replaceAll("<time>");
        clean = IDENTITY_HASH.matcher(clean).replaceAll("@<hash>");
        clean = PORT.matcher(clean).replaceAll("$1:<port>");
        clean = DURATION.matcher(clean).replaceAll("<duration>");
        clean = LINE_REF.matcher(clean).replaceAll("$1");
        clean = SPACES.matcher(clean).replaceAll(" ").strip();
        return clean.length() <= MAX_MESSAGE ? clean : clean.substring(0, MAX_MESSAGE);
    }

    /** SHA-256 of the identifying fields, as 16 hex characters. */
    public static String of(FailureType type, String module, @Nullable String file, @Nullable String symbol, String normalizedMessage) {
        String key = String.join("\u0000", type.name(), module, nullToEmpty(file), nullToEmpty(symbol), normalizedMessage);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
