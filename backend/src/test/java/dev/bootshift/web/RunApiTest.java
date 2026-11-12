package dev.bootshift.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.bootshift.TestDatabase;
import dev.bootshift.run.Run;
import dev.bootshift.run.RunState;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestDatabase.class)
@ActiveProfiles("test")
@DirtiesContext
@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
class RunApiTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private RestTestClient client;

    @Autowired
    private JsonMapper json;

    @Test
    void startReturns202WithLocationAndAQueuedRun() {
        var result = client.post().uri("/api/v1/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/demo-app"))
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().valueEquals(RunController.IDEMPOTENT_REPLAYED, "false")
                .expectBody(Run.class)
                .returnResult();

        Run run = result.getResponseBody();
        assertThat(run).isNotNull();
        assertThat(run.status()).isEqualTo(RunState.QUEUED);
        assertThat(run.repository()).isEqualTo("hsanjebri/demo-app");
        assertThat(result.getResponseHeaders().getLocation()).hasToString("/api/v1/runs/" + run.id());
    }

    @Test
    void fakeRunStreamsEveryStageAndEndsWithAPullRequest() {
        Run run = start("hsanjebri/stream-me");

        SseEvents stream = SseEvents.parse(events(run.id(), null), json);

        assertThat(stream.states()).containsExactly(
                "QUEUED", "CLONING", "BASELINE", "SCANNING", "RECIPE", "VERIFY", "REPAIR", "VERIFY", "REVIEW", "PR_OPENED");
        assertThat(stream.events()).extracting(SseEvents.Event::name).contains("LOG");
        assertThat(stream.events()).extracting(SseEvents.Event::id).isSorted().doesNotHaveDuplicates();

        Run finished = get(run.id());
        assertThat(finished.status()).isEqualTo(RunState.PR_OPENED);
        assertThat(finished.stage()).isEqualTo(RunState.REVIEW);
        assertThat(finished.startedAt()).isNotNull();
        assertThat(finished.finishedAt()).isNotNull();
    }

    @Test
    void lastEventIdReplaysOnlyWhatTheClientMissed() {
        Run run = start("hsanjebri/replay-me");
        SseEvents all = SseEvents.parse(events(run.id(), null), json);
        long resumeAfter = all.events().get(3).id();

        SseEvents rest = SseEvents.parse(events(run.id(), resumeAfter), json);

        assertThat(rest.events()).isNotEmpty();
        assertThat(rest.events()).allSatisfy(e -> assertThat(e.id()).isGreaterThan(resumeAfter));
        assertThat(rest.events()).hasSize(all.events().size() - 4);
    }

    @Test
    void sameIdempotencyKeyReturnsTheSameRun() {
        String key = UUID.randomUUID().toString();
        Run first = startWithKey("hsanjebri/idem", key);

        Run second = client.post().uri("/api/v1/runs")
                .header(RunController.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/idem"))
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().valueEquals(RunController.IDEMPOTENT_REPLAYED, "true")
                .expectBody(Run.class)
                .returnResult().getResponseBody();

        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());
    }

    @Test
    void reusingAnIdempotencyKeyForADifferentRequestIs422() {
        String key = UUID.randomUUID().toString();
        startWithKey("hsanjebri/first", key);

        JsonNode problem = client.post().uri("/api/v1/runs")
                .header(RunController.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/second"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
                .expectHeader().contentTypeCompatibleWith(PROBLEM)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.get("type").asString()).endsWith("/idempotency-key-reused");
    }

    @Test
    void invalidRepositoryNameIsRejectedAsProblemDetails() {
        client.post().uri("/api/v1/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "../../etc/passwd"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(PROBLEM);
    }

    @Test
    void unknownRunIs404ProblemDetails() {
        JsonNode problem = client.get().uri("/api/v1/runs/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(PROBLEM)
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.get("type").asString()).endsWith("/run-not-found");
    }

    @Test
    void unsupportedApiVersionIsRejected() {
        client.get().uri("/api/v2/runs").exchange().expectStatus().isBadRequest();
    }

    @Test
    void listShowsNewestRunsFirst() {
        Run older = start("hsanjebri/list-a");
        Run newer = start("hsanjebri/list-b");

        Run[] runs = client.get().uri("/api/v1/runs?limit=200").exchange()
                .expectStatus().isOk()
                .expectBody(Run[].class).returnResult().getResponseBody();

        assertThat(runs).isNotNull();
        var ids = java.util.Arrays.stream(runs).map(Run::id).toList();
        assertThat(ids).contains(older.id(), newer.id());
        assertThat(ids.indexOf(newer.id())).isLessThan(ids.indexOf(older.id()));
    }

    private Run start(String repository) {
        return startWithKey(repository, null);
    }

    private Run startWithKey(String repository, String key) {
        var request = client.post().uri("/api/v1/runs").contentType(MediaType.APPLICATION_JSON);
        if (key != null) {
            request = request.header(RunController.IDEMPOTENCY_KEY, key);
        }
        Run run = request.body(Map.of("repository", repository)).exchange()
                .expectStatus().isAccepted()
                .expectBody(Run.class).returnResult().getResponseBody();
        assertThat(run).isNotNull();
        return run;
    }

    private Run get(UUID id) {
        Run run = client.get().uri("/api/v1/runs/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody(Run.class).returnResult().getResponseBody();
        assertThat(run).isNotNull();
        return run;
    }

    private String events(UUID id, Long lastEventId) {
        var request = client.get().uri("/api/v1/runs/{id}/events", id).accept(MediaType.TEXT_EVENT_STREAM);
        if (lastEventId != null) {
            request = request.header("Last-Event-ID", Long.toString(lastEventId));
        }
        String body = request.exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body).isNotNull();
        return body;
    }
}
