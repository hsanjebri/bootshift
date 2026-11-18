package dev.bootshift.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import dev.bootshift.build.BuildKind;
import dev.bootshift.build.BuildReport;
import dev.bootshift.build.BuildResultStore;
import dev.bootshift.build.FailureType;
import dev.bootshift.build.JavaVersions;
import dev.bootshift.build.MavenBuild;
import dev.bootshift.git.GitClone;
import dev.bootshift.github.GitHubApp;
import dev.bootshift.repo.RepositoryName;
import dev.bootshift.run.MigrationPipeline;
import dev.bootshift.run.RunContext;
import dev.bootshift.run.RunEvent;
import dev.bootshift.run.RunState;
import dev.bootshift.sandbox.Sandbox;
import dev.bootshift.sandbox.Sandbox.Outcome;
import dev.bootshift.workspace.Workspaces;

/**
 * Clone and baseline for real (Phase 2). The later stages are still simulated and say so in
 * every log line, until Phases 3 to 5 replace them.
 */
@Component
@ConditionalOnProperty(name = "bootshift.pipeline.mode", havingValue = "real", matchIfMissing = true)
class RealMigrationPipeline implements MigrationPipeline {

    static final String SIMULATED = "[simulated] ";

    private final GitClone git;
    private final GitHubApp github;
    private final MavenBuild maven;
    private final BuildResultStore builds;
    private final Sandbox sandbox;
    private final Workspaces workspaces;
    private final Duration simulatedStepDelay;

    RealMigrationPipeline(GitClone git, GitHubApp github, MavenBuild maven, BuildResultStore builds, Sandbox sandbox,
                          Workspaces workspaces, dev.bootshift.run.RunProperties runProperties) {
        this.git = git;
        this.github = github;
        this.maven = maven;
        this.builds = builds;
        this.sandbox = sandbox;
        this.workspaces = workspaces;
        this.simulatedStepDelay = runProperties.pipeline().fakeStepDelay();
    }

    @Override
    public void execute(RunContext run) throws InterruptedException {
        UUID id = run.runId();
        try {
            Path repository = workspaces.repository(id);
            clone(run, repository);
            run.advance(RunState.BASELINE);
            if (!baseline(run, repository)) {
                return;
            }
            simulateRest(run);
        } finally {
            workspaces.delete(id);
            sandbox.release(id);
        }
    }

    private void clone(RunContext run, Path repository) {
        RepositoryName name = RepositoryName.parse(run.repository());
        String token = null;
        if (github.configured()) {
            run.log("Requesting a read-only token for " + name + " (expires within an hour)");
            token = github.cloneToken(name.owner(), name.name());
        } else {
            run.log("No GitHub App configured; cloning " + name + " as a public repository");
        }
        URI remote = URI.create("https://github.com/" + name.owner() + "/" + name.name() + ".git");
        GitClone.Cloned cloned = git.clone(remote, run.branch(), token, repository);
        run.recordDetails(cloned.sourceSha(), null);
        run.log("Cloned " + cloned.baseBranch() + " at " + cloned.sourceSha().substring(0, 7)
                + " and created " + GitClone.WORK_BRANCH + "; no credentials left in the working copy");
    }

    /** Returns false if the run stopped because the baseline does not build. */
    private boolean baseline(RunContext run, Path repository) throws InterruptedException {
        JavaVersions.Selection java;
        try {
            java = JavaVersions.select(repository);
        } catch (JavaVersions.UnsupportedJava e) {
            run.stop(RunState.NOT_ELIGIBLE, e.getMessage());
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        run.recordDetails(null, java.jdk());
        run.log(java.declared().isPresent()
                ? "Project targets Java " + java.declared().getAsInt() + "; building with JDK " + java.jdk()
                : "No Java version declared; building with JDK " + java.jdk());
        run.log("Baseline: mvn verify on the untouched project, in the sandbox");

        BuildReport report = maven.verify(run.runId(), BuildKind.BASELINE, repository, java.jdk(),
                workspaces.log(run.runId(), "baseline"), run::log, run::isCancelRequested);
        if (report.outcome() == Outcome.CANCELLED) {
            run.checkCancelled();
        }
        UUID buildId = builds.save(run.runId(), 0, report);
        run.event(RunEvent.BUILD_FINISHED, summary(buildId, report));
        run.log(describe(report));

        String reason = ineligibility(report);
        if (reason != null) {
            run.stop(RunState.NOT_ELIGIBLE, reason);
            return false;
        }
        return true;
    }

    /** README section 6: a baseline that does not compile stops the run. Failing tests are allowed. */
    static @Nullable String ineligibility(BuildReport report) {
        return switch (report.outcome()) {
            case TIMED_OUT -> "The baseline build did not finish within " + report.duration().toMinutes() + " minutes";
            case CANCELLED -> "The baseline build was cancelled";
            case EXITED -> {
                if (report.count(FailureType.DEPENDENCY) > 0) {
                    yield "The baseline cannot resolve its dependencies through the proxy";
                }
                if (report.count(FailureType.COMPILE) > 0) {
                    yield "The baseline does not compile (" + report.count(FailureType.COMPILE) + " compiler errors)";
                }
                yield null;
            }
        };
    }

    static String describe(BuildReport report) {
        String tests = report.testsRun() + " tests run, " + report.testsFailed() + " failing, " + report.testsSkipped() + " skipped";
        long other = report.count(FailureType.OTHER);
        return "Baseline finished in " + report.duration().toSeconds() + "s: " + tests
                + (report.testsFailed() > 0 ? ". Existing failures are recorded and will not count against the upgrade" : "")
                + (other > 0 ? ". " + other + " other build problem(s) recorded" : "");
    }

    static Map<String, Object> summary(UUID buildId, BuildReport report) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("buildId", buildId.toString());
        payload.put("kind", report.kind().name());
        payload.put("outcome", report.outcome().name());
        payload.put("exitCode", report.exitCode());
        payload.put("jdk", report.jdk());
        payload.put("testsRun", report.testsRun());
        payload.put("testsFailed", report.testsFailed());
        payload.put("testsSkipped", report.testsSkipped());
        payload.put("durationMs", report.duration().toMillis());
        payload.put("compileErrors", report.count(FailureType.COMPILE));
        payload.put("failures", report.failures().size());
        payload.put("logTruncated", report.logTruncated());
        return payload;
    }

    private void simulateRest(RunContext run) throws InterruptedException {
        run.advance(RunState.SCANNING);
        step(run, "Stages after the baseline are simulated until Phases 3 to 5 replace them");
        run.advance(RunState.RECIPE);
        step(run, "Applied UpgradeSpringBoot_4_0 and dev.bootshift.UpgradeSpringBoot_4_1");
        run.advance(RunState.VERIFY);
        step(run, "Verify: no new failures");
        run.advance(RunState.REVIEW);
        step(run, "Review guards passed; no pull request is opened yet");
        run.advance(RunState.PR_OPENED);
    }

    private void step(RunContext run, String message) throws InterruptedException {
        run.log(SIMULATED + message);
        if (!simulatedStepDelay.isZero()) {
            Thread.sleep(simulatedStepDelay);
        }
    }
}
