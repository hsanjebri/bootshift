package dev.bootshift.build;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import dev.bootshift.sandbox.Sandbox;
import dev.bootshift.sandbox.SandboxProperties;

/**
 * Runs {@code mvn verify} for a working copy in the sandbox and turns the output into a
 * {@link BuildReport}. Test failures do not stop the build, so one build reports all of them.
 */
public class MavenBuild {

    /**
     * {@code -fae} keeps building independent modules after a failure, and
     * {@code maven.test.failure.ignore} lets every module run its tests; results come from the reports.
     */
    static final List<String> VERIFY = List.of(
            "mvn", "-B", "-ntp", "-fae", "-Dmaven.test.failure.ignore=true", "clean", "verify");

    static final int MAX_ERROR_LINES = 20_000;

    /** Module headers, Maven's verdict, test totals and errors; not stack traces or per-class lines. */
    private static final Pattern PROGRESS = Pattern.compile(
            "^\\[INFO] Building (?!jar|war).+|^\\[INFO] BUILD .+|^\\[(INFO|WARNING|ERROR)] Tests run: \\d+, Failures: \\d+, Errors: \\d+, Skipped: \\d+$"
                    + "|^\\[ERROR] (?!\\s|\\t|-> |Re-run|To see|For more|Failed to execute goal .+ -> \\[Help).+"
                    + "|^\\[bootshift] .+");

    private final Sandbox sandbox;
    private final SandboxProperties properties;

    public MavenBuild(Sandbox sandbox, SandboxProperties properties) {
        this.sandbox = sandbox;
        this.properties = properties;
    }

    /**
     * @param progress  receives the lines worth showing live; the full output goes to {@code logFile}
     * @param cancelled polled during the build; true kills it
     */
    public BuildReport verify(UUID runId, BuildKind kind, Path repository, int jdk, Path logFile,
                              Consumer<String> progress, BooleanSupplier cancelled) throws InterruptedException {
        List<String> errors = new ArrayList<>();
        Sandbox.Command command = new Sandbox.Command(runId, kind.name().toLowerCase(), repository, jdk, VERIFY,
                properties.buildTimeout(), logFile);
        ErrorLines errorLines = new ErrorLines(errors);
        Sandbox.Result result = sandbox.run(command, line -> {
            errorLines.accept(line);
            if (isProgress(line)) {
                progress.accept(line);
            }
        }, cancelled);

        List<Failure> failures = new ArrayList<>(MavenLog.parse(errors, result.outcome() == Sandbox.Outcome.EXITED ? result.exitCode() : 0));
        SurefireReports.Summary tests;
        try {
            tests = SurefireReports.read(repository);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read test reports in " + repository, e);
        }
        failures.addAll(tests.failures());
        return new BuildReport(kind, jdk, result.outcome(), result.exitCode(), result.duration(),
                tests.run(), tests.failed(), tests.skipped(), failures, result.logFile(), result.logTruncated());
    }

    /**
     * Keeps {@code [ERROR]} lines and the indented lines right after them: in the compiler's own
     * section, javac's "  symbol:" and "  location:" lines have no level prefix.
     */
    static final class ErrorLines implements Consumer<String> {

        private final List<String> lines;
        private boolean inError;

        ErrorLines(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public void accept(String line) {
            inError = line.startsWith("[ERROR]") || (inError && line.startsWith("  "));
            if (inError && lines.size() < MAX_ERROR_LINES) {
                lines.add(line);
            }
        }
    }

    static boolean isProgress(String line) {
        return PROGRESS.matcher(line).matches();
    }
}
