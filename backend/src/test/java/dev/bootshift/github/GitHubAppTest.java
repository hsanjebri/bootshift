package dev.bootshift.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** GitHubApp against a mocked GitHub REST API. */
class GitHubAppTest {

    private static final String API = "https://api.github.com";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    private MockRestServiceServer server;
    private GitHubApp github;

    @BeforeEach
    void setUp() throws Exception {
        Path key = tmp.resolve("app.pem");
        Files.writeString(key, TestKeys.pkcs1Pem(TestKeys.rsa()));
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        server = MockRestServiceServer.bindTo(builder).build();
        GitHubApi api = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build()
                .createClient(GitHubApi.class);
        github = new GitHubApp(new GitHubProperties(42L, "bootshift-test", key, URI.create(API)), api, CLOCK);
    }

    @Test
    void reportsNotConfiguredWithoutCallingGitHub() {
        GitHubApi unused = null;
        GitHubApp unconfigured = new GitHubApp(new GitHubProperties(null, null, null, URI.create(API)), unused, CLOCK);

        GitHubApp.Status status = unconfigured.status();

        assertThat(status.configured()).isFalse();
        assertThat(status.installations()).isEmpty();
    }

    @Test
    void statusListsInstallationsUsingTheAppJwt() {
        server.expect(requestTo(API + "/app/installations?per_page=100"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ey")))
                .andRespond(json(installations(7)));

        GitHubApp.Status status = github.status();

        assertThat(status.configured()).isTrue();
        assertThat(status.installUrl()).isEqualTo("https://github.com/apps/bootshift-test/installations/new");
        assertThat(status.installations()).singleElement()
                .satisfies(i -> assertThat(i.account()).isEqualTo("hsanjebri"));
    }

    @Test
    void listsRepositoriesAcrossPagesAndCachesTheInstallationToken() {
        server.expect(twice(), requestTo(API + "/app/installations?per_page=100")).andRespond(json(installations(7)));
        server.expect(once(), requestTo(API + "/app/installations/7/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(json("{\"token\":\"ghs_test\",\"expires_at\":\"2026-11-14T13:00:00Z\"}"));
        server.expect(requestTo(API + "/installation/repositories?per_page=100&page=1"))
                .andExpect(header("Authorization", "Bearer ghs_test"))
                .andRespond(json(repositories(100, 0)));
        server.expect(requestTo(API + "/installation/repositories?per_page=100&page=2"))
                .andRespond(json(repositories(3, 100)));
        server.expect(requestTo(API + "/installation/repositories?per_page=100&page=1"))
                .andExpect(header("Authorization", "Bearer ghs_test"))
                .andRespond(json(repositories(1, 0)));

        assertThat(github.repositories()).hasSize(103)
                .first().satisfies(r -> assertThat(r.fullName()).isEqualTo("hsanjebri/repo-0"));
        // Second listing reuses the cached token: only one access_tokens call is expected.
        assertThat(github.repositories()).hasSize(1);
        server.verify();
    }

    @Test
    void wrapsGitHubFailuresWithoutLeakingTokens() {
        server.expect(requestTo(API + "/app/installations?per_page=100")).andRespond(withServerError());

        assertThatThrownBy(() -> github.repositories())
                .isInstanceOf(GitHubApp.GitHubUnavailable.class)
                .hasMessageNotContaining("Bearer");
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static String installations(long id) {
        return "[{\"id\":" + id + ",\"account\":{\"login\":\"hsanjebri\",\"type\":\"User\"},\"repository_selection\":\"selected\"}]";
    }

    private static String repositories(int count, int offset) {
        String repos = IntStream.range(offset, offset + count)
                .mapToObj(i -> """
                        {"id":%d,"name":"repo-%d","full_name":"hsanjebri/repo-%d","owner":{"login":"hsanjebri","type":"User"},
                         "private":true,"archived":false,"default_branch":"main","html_url":"https://github.com/hsanjebri/repo-%d",
                         "pushed_at":"2026-11-01T10:00:00Z"}""".formatted(i, i, i, i))
                .collect(Collectors.joining(","));
        return "{\"total_count\":" + count + ",\"repositories\":[" + repos + "]}";
    }
}
