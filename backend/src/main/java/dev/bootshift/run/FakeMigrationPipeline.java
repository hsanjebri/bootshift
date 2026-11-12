package dev.bootshift.run;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Phase 1 stand-in for the real pipeline. Walks a run through every stage, including one repair
 * iteration, without cloning, building or calling a model. Every log line says it is fake.
 */
@Component
@ConditionalOnProperty(name = "bootshift.pipeline.mode", havingValue = "fake", matchIfMissing = true)
class FakeMigrationPipeline implements MigrationPipeline {

    private final Duration stepDelay;

    FakeMigrationPipeline(RunProperties properties) {
        this.stepDelay = properties.pipeline().fakeStepDelay();
    }

    @Override
    public void execute(RunContext run) throws InterruptedException {
        step(run, "[fake] Cloning " + run.repository() + " into a throwaway working copy");
        run.advance(RunState.BASELINE);
        step(run, "[fake] Baseline build: 12 tests run, 0 failing");
        run.advance(RunState.SCANNING);
        step(run, "[fake] Detected Spring Boot 3.5.16, Java 17, single module");
        run.advance(RunState.RECIPE);
        step(run, "[fake] Applied UpgradeSpringBoot_4_0 and dev.bootshift.UpgradeSpringBoot_4_1");
        run.advance(RunState.VERIFY);
        step(run, "[fake] Verify: 1 compile error left after recipes");
        run.advance(RunState.REPAIR);
        step(run, "[fake] Repair iteration 1: replaced AntPathRequestMatcher with PathPatternRequestMatcher");
        run.advance(RunState.VERIFY);
        step(run, "[fake] Verify: 12 tests run, 0 failing");
        run.advance(RunState.REVIEW);
        step(run, "[fake] Review guards passed");
        run.advance(RunState.PR_OPENED);
    }

    private void step(RunContext run, String message) throws InterruptedException {
        run.log(message);
        if (!stepDelay.isZero()) {
            Thread.sleep(stepDelay);
        }
    }
}
