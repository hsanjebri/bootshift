package dev.bootshift.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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
import dev.bootshift.build.BuildKind;
import dev.bootshift.build.BuildReport;
import dev.bootshift.build.BuildResultStore;
import dev.bootshift.build.Failure;
import dev.bootshift.build.FailureType;
import dev.bootshift.build.Fingerprints;
import dev.bootshift.run.Run;
import dev.bootshift.sandbox.Sandbox.Outcome;
import tools.jackson.databind.JsonNode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "bootshift.runs.worker-enabled=false")
@AutoConfigureRestTestClient
@Import(TestDatabase.class)
@ActiveProfiles("test")
@DirtiesContext
class BuildApiTest {

    @Autowired
    private RestTestClient client;

    @Autowired
    private BuildResultStore builds;

    @Test
    void listsTheBuildsOfARunWithTheirFailures() {
        Run run = client.post().uri("/api/v1/runs").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/builds")).exchange()
                .expectStatus().isAccepted().expectBody(Run.class).returnResult().getResponseBody();
        assertThat(run).isNotNull();
        Failure compile = new Failure(FailureType.COMPILE, ".", "src/main/java/A.java", 12, "class Foo", "cannot find symbol",
                Fingerprints.of(FailureType.COMPILE, ".", "src/main/java/A.java", "class Foo", "cannot find symbol"));
        builds.save(run.id(), 0, new BuildReport(BuildKind.BASELINE, 17, Outcome.EXITED, 1, Duration.ofSeconds(42), 9, 2, 1,
                List.of(compile), Path.of("/var/bootshift/logs/x/baseline.log"), false));

        JsonNode body = client.get().uri("/api/v1/runs/{id}/builds", run.id()).exchange()
                .expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(body).hasSize(1);
        JsonNode build = body.get(0);
        assertThat(build.get("kind").asString()).isEqualTo("BASELINE");
        assertThat(build.get("outcome").asString()).isEqualTo("EXITED");
        assertThat(build.get("exitCode").asInt()).isEqualTo(1);
        assertThat(build.get("jdk").asInt()).isEqualTo(17);
        assertThat(build.get("testsRun").asInt()).isEqualTo(9);
        assertThat(build.get("durationMs").asLong()).isEqualTo(42_000);
        assertThat(build.has("logPath")).as("server paths are not exposed").isFalse();
        JsonNode failure = build.get("failures").get(0);
        assertThat(failure.get("type").asString()).isEqualTo("COMPILE");
        assertThat(failure.get("symbol").asString()).isEqualTo("class Foo");
        assertThat(failure.get("line").asInt()).isEqualTo(12);
        assertThat(failure.get("fingerprint").asString()).isEqualTo(compile.fingerprint());
    }

    @Test
    void unknownRunIs404() {
        client.get().uri("/api/v1/runs/{id}/builds", UUID.randomUUID()).exchange().expectStatus().isNotFound();
    }
}
