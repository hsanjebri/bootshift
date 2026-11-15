package dev.bootshift.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.bootshift.build.BuildKind;
import dev.bootshift.build.BuildReport;
import dev.bootshift.build.Failure;
import dev.bootshift.build.FailureType;
import dev.bootshift.build.Fingerprints;
import dev.bootshift.sandbox.Sandbox.Outcome;

class RealMigrationPipelineTest {

    @Test
    void failingTestsDoNotStopTheRun() {
        assertThat(RealMigrationPipeline.ineligibility(report(Outcome.EXITED, failure(FailureType.TEST), failure(FailureType.OTHER))))
                .isNull();
    }

    @Test
    void compilerAndDependencyErrorsMakeTheProjectIneligible() {
        assertThat(RealMigrationPipeline.ineligibility(report(Outcome.EXITED, failure(FailureType.COMPILE))))
                .isEqualTo("The baseline does not compile (1 compiler errors)");
        assertThat(RealMigrationPipeline.ineligibility(report(Outcome.EXITED, failure(FailureType.DEPENDENCY))))
                .contains("cannot resolve its dependencies");
        assertThat(RealMigrationPipeline.ineligibility(report(Outcome.TIMED_OUT))).contains("did not finish");
    }

    @Test
    void describesTheBaseline() {
        assertThat(RealMigrationPipeline.describe(report(Outcome.EXITED, failure(FailureType.TEST))))
                .isEqualTo("Baseline finished in 95s: 10 tests run, 1 failing, 0 skipped."
                        + " Existing failures are recorded and will not count against the upgrade");
    }

    private static BuildReport report(Outcome outcome, Failure... failures) {
        int failing = (int) List.of(failures).stream().filter(f -> f.type() == FailureType.TEST).count();
        return new BuildReport(BuildKind.BASELINE, 17, outcome, 0, Duration.ofSeconds(95), 10, failing, 0, List.of(failures),
                Path.of("baseline.log"), false);
    }

    private static Failure failure(FailureType type) {
        return new Failure(type, ".", "A.java", 1, null, "boom", Fingerprints.of(type, ".", "A.java", null, "boom"));
    }
}
