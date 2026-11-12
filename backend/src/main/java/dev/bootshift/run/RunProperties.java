package dev.bootshift.run;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code bootshift.runs.*} and {@code bootshift.pipeline.*}.
 *
 * @param runs     worker and queue settings
 * @param pipeline which pipeline executes runs
 */
@ConfigurationProperties("bootshift")
public record RunProperties(@DefaultValue Runs runs, @DefaultValue Pipeline pipeline) {

    /**
     * @param maxConcurrent  runs processed at the same time (README section 16: default 2)
     * @param pollInterval   how often an idle worker checks the queue
     * @param workerEnabled  false disables processing, e.g. for tests that inspect queued runs
     */
    public record Runs(
            @DefaultValue("2") int maxConcurrent,
            @DefaultValue("500ms") Duration pollInterval,
            @DefaultValue("true") boolean workerEnabled) {
    }

    /**
     * @param mode          only {@code fake} exists in Phase 1
     * @param fakeStepDelay pause between fake stages, so the dashboard has something to watch
     */
    public record Pipeline(
            @DefaultValue("fake") String mode,
            @DefaultValue("800ms") Duration fakeStepDelay) {
    }
}
