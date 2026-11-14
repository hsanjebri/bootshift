package dev.bootshift.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.bootshift.TestDatabase;
import dev.bootshift.repo.AllowlistPolicy;
import dev.bootshift.repo.RepositoryName;
import dev.bootshift.repo.RepositoryService;
import tools.jackson.databind.JsonNode;

/**
 * Allowlist enforcement and the GitHub endpoints when no GitHub App is configured. The policy is
 * forced on, as it is once an app is connected.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "bootshift.runs.worker-enabled=false")
@AutoConfigureRestTestClient
@Import({TestDatabase.class, AllowlistApiTest.EnforcedPolicy.class})
@ActiveProfiles("test")
@DirtiesContext
class AllowlistApiTest {

    @TestConfiguration
    static class EnforcedPolicy {
        @Bean
        @Primary
        AllowlistPolicy enforcedAllowlist() {
            return () -> true;
        }
    }

    @Autowired
    private RestTestClient client;

    @Autowired
    private RepositoryService repositories;

    @Test
    void runsOnRepositoriesOffTheAllowlistAreRefused() {
        JsonNode problem = client.post().uri("/api/v1/runs").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/not-allowed"))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.get("type").asString()).endsWith("/repository-not-allowed");
    }

    @Test
    void allowlistedRepositoriesCanRunAndNamesMatchCaseInsensitively() {
        repositories.allow(new RepositoryName("hsanjebri", "Allowed-App"),
                new RepositoryService.GitHubDetails(1L, 7L, true, "https://github.com/hsanjebri/Allowed-App"));

        client.post().uri("/api/v1/runs").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("repository", "hsanjebri/allowed-app"))
                .exchange()
                .expectStatus().isAccepted();

        JsonNode list = client.get().uri("/api/v1/repositories").exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();
        assertThat(list).isNotNull();
        assertThat(list.valueStream().map(r -> r.get("name").asString())).contains("Allowed-App");
    }

    @Test
    void disallowingRemovesTheRepositoryFromTheAllowlist() {
        RepositoryName name = new RepositoryName("hsanjebri", "short-lived");
        repositories.allow(name, new RepositoryService.GitHubDetails(2L, 7L, false, "https://github.com/hsanjebri/short-lived"));
        repositories.disallow(name);

        assertThat(repositories.isAllowed(name)).isFalse();
    }

    @Test
    void githubStatusSaysNotConnectedWhenNoAppIsConfigured() {
        JsonNode status = client.get().uri("/api/v1/github/status").exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class).returnResult().getResponseBody();

        assertThat(status).isNotNull();
        assertThat(status.get("configured").asBoolean()).isFalse();
    }

    @Test
    void changingTheAllowlistNeedsAConnectedGitHubApp() {
        client.put().uri("/api/v1/repositories/hsanjebri/anything").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("allowed", true))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void invalidRepositoryNamesInThePathAreRejected() {
        client.put().uri("/api/v1/repositories/-bad-/name").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("allowed", true))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
