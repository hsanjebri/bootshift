package dev.bootshift.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Finds compiler, dependency and other build errors in Maven output. Paths are as the sandbox
 * sees them, under {@code /workspace}. Test failures come from {@link SurefireReports} instead.
 */
public final class MavenLog {

    static final int MAX_FAILURES = 500;

    // [ERROR] /workspace/app/src/main/java/a/B.java:[12,34] cannot find symbol
    private static final Pattern COMPILE = Pattern.compile(
            "^\\[ERROR] (?:/workspace/)?(\\S+?\\.(?:java|kt|groovy|scala)):\\[(\\d+)(?:,\\d+)?] (.+)$");
    // "[ERROR]   symbol:   class X" in Maven's summary; "  symbol:   class X", without the prefix,
    // in the compiler's own section.
    private static final Pattern SYMBOL = Pattern.compile("^(?:\\[ERROR])?\\s+symbol:\\s+(.+)$");
    private static final Pattern DEPENDENCY = Pattern.compile(
            "Could not resolve dependencies|Non-resolvable parent POM|Non-resolvable import POM|Could not transfer artifact"
                    + "|Could not find artifact|Plugin .+ or one of its dependencies could not be resolved|Unresolveable build extension");
    private static final Pattern COORDINATES = Pattern.compile("\\b[\\w.-]+:[\\w.-]+(?::[\\w.-]+){1,3}\\b");
    private static final Pattern FAILED_GOAL = Pattern.compile("^\\[ERROR] (Failed to execute goal .+)$");
    private static final Pattern ON_PROJECT = Pattern.compile(" on project [\\w.-]+");

    private MavenLog() {
    }

    /**
     * @param lines    the build output
     * @param exitCode used to add an {@link FailureType#OTHER} failure when the build failed without a recognizable cause
     */
    public static List<Failure> parse(List<String> lines, int exitCode) {
        Map<String, Failure> found = new LinkedHashMap<>();
        String firstFailedGoal = null;
        for (int i = 0; i < lines.size() && found.size() < MAX_FAILURES; i++) {
            String line = stripCarriageReturn(lines.get(i));
            Matcher compile = COMPILE.matcher(line);
            if (compile.matches()) {
                String file = compile.group(1);
                String symbol = symbolAfter(lines, i);
                Failure failure = Failure.of(FailureType.COMPILE, module(file), file,
                        Integer.parseInt(compile.group(2)), symbol, compile.group(3));
                // Maven prints each compiler error twice: once while compiling and once in the summary.
                found.putIfAbsent(failure.fingerprint() + ":" + failure.line(), failure);
                continue;
            }
            if (line.startsWith("[ERROR]") && DEPENDENCY.matcher(line).find()) {
                String message = ON_PROJECT.matcher(line.substring("[ERROR]".length()).strip()).replaceFirst("");
                Failure failure = Failure.of(FailureType.DEPENDENCY, ".", null, null, coordinates(message), message);
                found.putIfAbsent(failure.fingerprint(), failure);
                continue;
            }
            Matcher goal = FAILED_GOAL.matcher(line);
            if (goal.matches() && firstFailedGoal == null) {
                firstFailedGoal = goal.group(1);
            }
        }
        List<Failure> failures = new ArrayList<>(found.values());
        if (failures.isEmpty() && exitCode != 0) {
            String message = firstFailedGoal != null
                    ? ON_PROJECT.matcher(firstFailedGoal).replaceFirst("")
                    : "Build failed with exit code " + exitCode;
            failures.add(Failure.of(FailureType.OTHER, ".", null, null, goalOf(message), message));
        }
        return List.copyOf(failures);
    }

    /** {@code app/src/main/java/A.java} is in module {@code app}; {@code src/main/java/A.java} in the root. */
    static String module(String file) {
        int src = file.startsWith("src/") ? 0 : file.indexOf("/src/");
        return src <= 0 ? "." : file.substring(0, src);
    }

    private static @Nullable String symbolAfter(List<String> lines, int index) {
        for (int j = index + 1; j < Math.min(lines.size(), index + 4); j++) {
            String next = stripCarriageReturn(lines.get(j));
            Matcher symbol = SYMBOL.matcher(next);
            if (symbol.matches()) {
                return symbol.group(1).strip();
            }
            if (COMPILE.matcher(next).matches()) {
                break;
            }
        }
        return null;
    }

    private static @Nullable String coordinates(String message) {
        Matcher matcher = COORDINATES.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }

    /** {@code Failed to execute goal org.x:plugin:1.0:check (id) ...} has goal {@code org.x:plugin:1.0:check}. */
    private static @Nullable String goalOf(String message) {
        return message.startsWith("Failed to execute goal ") ? coordinates(message) : null;
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
