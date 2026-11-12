package dev.bootshift.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.bootshift.TestDatabase;
import dev.bootshift.run.Run;
import dev.bootshift.run.RunState;

/** Slow fake steps, so the test can cancel a run while the worker is in the middle of it. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "bootshift.pipeline.fake-step-delay=300ms")
@AutoConfigureRestTestClient
@Import(TestDatabase.class)
@ActiveProfiles("test")
@DirtiesContext
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
class RunCancelWhileRunningTest {

    @Autowired
    private RestTestClient client;

    @Test
    void cancelStopsARunningRunAtTheNextStageBoundary() throws InterruptedException {
        Run run = client.post().uri("/api/v1/runs").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/slow-run")).exchange()
                .expectStatus().isAccepted()
                .expectBody(Run.class).returnResult().getResponseBody();
        assertThat(run).isNotNull();

        waitFor(run.id(), r -> r.status().isActive());
        Run flagged = client.post().uri("/api/v1/runs/{id}/cancel", run.id()).exchange()
                .expectStatus().isAccepted()
                .expectBody(Run.class).returnResult().getResponseBody();
        assertThat(flagged).isNotNull();
        assertThat(flagged.cancelRequested()).isTrue();

        Run cancelled = waitFor(run.id(), r -> r.status().isTerminal());
        assertThat(cancelled.status()).isEqualTo(RunState.CANCELLED);
        assertThat(cancelled.stage()).as("records where it stopped").isNotNull().isNotEqualTo(RunState.REVIEW);
    }

    private Run waitFor(UUID id, Predicate<Run> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            Run current = client.get().uri("/api/v1/runs/{id}", id).exchange()
                    .expectBody(Run.class).returnResult().getResponseBody();
            if (current != null && condition.test(current)) {
                return current;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Run " + id + " did not reach the expected state in time");
    }
}
