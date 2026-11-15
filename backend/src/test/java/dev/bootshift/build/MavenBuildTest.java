package dev.bootshift.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import dev.bootshift.sandbox.Sandbox;
import dev.bootshift.sandbox.SandboxProperties;

class MavenBuildTest {

    @TempDir
    Path repo;

    final SandboxProperties properties = new SandboxProperties("img", "net", "http://proxy", DataSize.ofGigabytes(4), 2, 512,
            DataSize.ofGigabytes(1), Duration.ofMinutes(20), DataSize.ofMegabytes(50), null);

    @Test
    void combinesCompilerErrorsAndTestReports() throws Exception {
        List<Sandbox.Command> commands = new ArrayList<>();
        Sandbox sandbox = new FakeSandbox(commands, 1, List.of(
                "[INFO] Building shop 1.0",
                "[ERROR] /workspace/src/main/java/A.java:[3,1] cannot find symbol",
                "[ERROR]   symbol:   class Foo",
                "\tat some.Stack(Trace.java:1)",
                "[INFO] BUILD FAILURE"), () -> {
            Path report = repo.resolve("target/surefire-reports/TEST-x.xml");
            Files.createDirectories(report.getParent());
            Files.writeString(report, SurefireReportsTest.REPORT);
        });
        List<String> progress = new ArrayList<>();

        BuildReport report = new MavenBuild(sandbox, properties).verify(UUID.randomUUID(), BuildKind.BASELINE, repo, 17,
                repo.resolveSibling("baseline.log"), progress::add, () -> false);

        assertThat(commands).singleElement().satisfies(c -> {
            assertThat(c.argv()).isEqualTo(MavenBuild.VERIFY);
            assertThat(c.jdk()).isEqualTo(17);
            assertThat(c.name()).isEqualTo("baseline");
            assertThat(c.timeout()).isEqualTo(Duration.ofMinutes(20));
        });
        assertThat(report.testsRun()).isEqualTo(4);
        assertThat(report.testsFailed()).isEqualTo(2);
        assertThat(report.count(FailureType.COMPILE)).isEqualTo(1);
        assertThat(report.count(FailureType.TEST)).isEqualTo(1);
        assertThat(report.count(FailureType.STARTUP)).isEqualTo(1);
        assertThat(report.compiles()).isFalse();
        assertThat(progress).containsExactly(
                "[INFO] Building shop 1.0", "[ERROR] /workspace/src/main/java/A.java:[3,1] cannot find symbol", "[INFO] BUILD FAILURE");
    }

    @Test
    void failingTestsAloneStillCompile() throws Exception {
        Sandbox sandbox = new FakeSandbox(new ArrayList<>(), 0, List.of("[INFO] BUILD SUCCESS"), () -> {
            Path report = repo.resolve("target/surefire-reports/TEST-x.xml");
            Files.createDirectories(report.getParent());
            Files.writeString(report, SurefireReportsTest.REPORT);
        });

        BuildReport report = new MavenBuild(sandbox, properties).verify(UUID.randomUUID(), BuildKind.BASELINE, repo, 17,
                repo.resolveSibling("b.log"), l -> { }, () -> false);

        assertThat(report.compiles()).isTrue();
        assertThat(report.testsFailed()).isEqualTo(2);
    }

    @Test
    void showsOnlyUsefulProgressLines() {
        assertThat(MavenBuild.isProgress("[INFO] Building api 1.0-SNAPSHOT  [2/3]")).isTrue();
        assertThat(MavenBuild.isProgress("[INFO] Building jar: /workspace/target/x.jar")).isFalse();
        assertThat(MavenBuild.isProgress("[WARNING] Tests run: 12, Failures: 1, Errors: 0, Skipped: 0")).isTrue();
        assertThat(MavenBuild.isProgress("[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.2 s -- in a.B")).isFalse();
        assertThat(MavenBuild.isProgress("[ERROR]   symbol:   class Foo")).isFalse();
        assertThat(MavenBuild.isProgress("[ERROR] -> [Help 1]")).isFalse();
        assertThat(MavenBuild.isProgress("[INFO] Downloaded from bootshift-proxy: http://reposilite:8080/x.jar")).isFalse();
        assertThat(MavenBuild.isProgress("[bootshift] Build killed after PT20M")).isTrue();
    }

    @FunctionalInterface
    interface Effect {
        void run() throws Exception;
    }

    /** Replays fixed output and runs an effect on the working copy, like a build writing reports. */
    record FakeSandbox(List<Sandbox.Command> commands, int exitCode, List<String> output, Effect effect) implements Sandbox {

        @Override
        public Result run(Command command, java.util.function.Consumer<String> lines, java.util.function.BooleanSupplier cancelled) {
            commands.add(command);
            output.forEach(lines);
            try {
                effect.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return new Result(Outcome.EXITED, exitCode, Duration.ofSeconds(3), command.logFile(), false);
        }

        @Override
        public void release(UUID runId) {
        }
    }
}
