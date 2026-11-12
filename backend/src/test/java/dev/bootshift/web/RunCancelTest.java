package dev.bootshift.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.TimeUnit;

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
import tools.jackson.databind.json.JsonMapper;

/** Worker disabled, so runs stay QUEUED until the test cancels them. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "bootshift.runs.worker-enabled=false")
@AutoConfigureRestTestClient
@Import(TestDatabase.class)
@ActiveProfiles("test")
@DirtiesContext
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
class RunCancelTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private JsonMapper json;

    @Test
    void cancellingAQueuedRunEndsItAndClosesTheStream() {
        Run run = start();

        Run cancelled = client.post().uri("/api/v1/runs/{id}/cancel", run.id()).exchange()
                .expectStatus().isAccepted()
                .expectBody(Run.class).returnResult().getResponseBody();

        assertThat(cancelled).isNotNull();
        assertThat(cancelled.status()).isEqualTo(RunState.CANCELLED);
        assertThat(cancelled.finishedAt()).isNotNull();

        String body = client.get().uri("/api/v1/runs/{id}/events", run.id()).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(SseEvents.parse(body, json).states()).containsExactly("QUEUED", "CANCELLED");
    }

    @Test
    void cancellingAFinishedRunIs409() {
        Run run = start();
        client.post().uri("/api/v1/runs/{id}/cancel", run.id()).exchange().expectStatus().isAccepted();

        client.post().uri("/api/v1/runs/{id}/cancel", run.id()).exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private Run start() {
        Run run = client.post().uri("/api/v1/runs").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/cancel-me")).exchange()
                .expectStatus().isAccepted()
                .expectBody(Run.class).returnResult().getResponseBody();
        assertThat(run).isNotNull();
        return run;
    }
}
