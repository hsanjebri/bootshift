package dev.bootshift.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.bootshift.TestDatabase;
import tools.jackson.databind.JsonNode;

/** The Connect GitHub endpoints, with no app connected. GitHub itself is covered by GitHubManifestTest. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "bootshift.runs.worker-enabled=false")
@AutoConfigureRestTestClient
@Import(TestDatabase.class)
@ActiveProfiles("test")
class GitHubConnectApiTest {

    @Autowired
    private RestTestClient client;

    @Test
    void statusSaysNotConnected() {
        JsonNode status = client.get().uri("/api/v1/github/status").exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(status).isNotNull();
        assertThat(status.get("configured").asBoolean()).isFalse();
        assertThat(status.get("source").isNull()).isTrue();
    }

    @Test
    void startsAFlowForALocalDashboard() {
        JsonNode form = client.post().uri("/api/v1/github/manifest").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("dashboardUrl", "http://localhost:4321"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(form).isNotNull();
        assertThat(form.get("action").asString()).startsWith("https://github.com/settings/apps/new?state=");
        assertThat(form.get("manifest").asString()).contains("\"redirect_url\":\"http://localhost:4321/api/v1/github/manifest/callback\"");
    }

    @Test
    void refusesARemoteDashboard() {
        client.post().uri("/api/v1/github/manifest").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("dashboardUrl", "https://evil.example"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void refusesACallbackItDidNotStart() {
        JsonNode problem = client.get().uri("/api/v1/github/manifest/callback?code=abc&state=forged").exchange()
                .expectStatus().isBadRequest()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.get("type").asString()).endsWith("/github-connect-expired");
    }

    @Test
    void disconnectingWithNothingSavedIsHarmless() {
        client.delete().uri("/api/v1/github/app").exchange().expectStatus().isNoContent();
    }
}
