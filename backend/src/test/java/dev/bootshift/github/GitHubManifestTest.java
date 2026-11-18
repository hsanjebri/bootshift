package dev.bootshift.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The Connect GitHub button's manifest flow against a mocked GitHub. */
class GitHubManifestTest {

    private static final String API = "https://api.github.com";
    private static final String DASHBOARD = "http://localhost:4321";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path tmp;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T10:00:00Z"));
    private MockRestServiceServer server;
    private GitHubApi api;
    private GitHubCredentials credentials;
    private GitHubManifest manifest;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(API);
        server = MockRestServiceServer.bindTo(builder).build();
        api = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build()
                .createClient(GitHubApi.class);
        GitHubProperties properties = properties(null);
        credentials = new GitHubCredentials(properties, JSON, clock);
        manifest = new GitHubManifest(properties, credentials, api, JSON, clock);
    }

    @Test
    void describesAPrivateAppWithTheReadmePermissionsAndNoWebhook() {
        GitHubManifest.Form form = manifest.start(DASHBOARD + "/", null);

        assertThat(form.action()).matches("https://github\\.com/settings/apps/new\\?state=[A-Za-z0-9_-]{43}");
        JsonNode app = JSON.readTree(form.manifest());
        assertThat(app.get("name").asString()).matches("bootshift-[0-9a-f]{6}");
        assertThat(app.get("url").asString()).isEqualTo(DASHBOARD);
        assertThat(app.get("public").asBoolean()).isFalse();
        assertThat(app.get("redirect_url").asString()).isEqualTo(DASHBOARD + "/api/v1/github/manifest/callback");
        assertThat(app.get("setup_url").asString()).isEqualTo(DASHBOARD + "/repositories?installed=1");
        assertThat(app.get("hook_attributes").get("active").asBoolean()).isFalse();
        assertThat(JSON.convertValue(app.get("default_permissions"), java.util.Map.class))
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "contents", "write", "pull_requests", "write", "metadata", "read"));
    }

    @Test
    void canCreateTheAppUnderAnOrganization() {
        assertThat(manifest.start(DASHBOARD, " my-org ").action())
                .startsWith("https://github.com/organizations/my-org/settings/apps/new?state=");
        assertThatThrownBy(() -> manifest.start(DASHBOARD, "bad/org"))
                .isInstanceOf(GitHubManifest.InvalidRequest.class);
    }

    @Test
    void acceptsOnlyALocalhostDashboardUnlessOneIsConfigured() {
        for (String ok : new String[] {"http://localhost:4321", "http://127.0.0.1:8080", "http://[::1]:4321", "https://LOCALHOST"}) {
            assertThat(manifest.dashboard(ok)).as(ok).isNotNull();
        }
        for (String bad : new String[] {"https://evil.example", "http://localhost.evil.example", "http://localhost:4321/x",
                "http://user@localhost", "javascript:alert(1)", "", "not a url"}) {
            assertThatThrownBy(() -> manifest.dashboard(bad)).as(bad).isInstanceOf(GitHubManifest.InvalidRequest.class);
        }

        GitHubProperties configured = properties(URI.create("https://bootshift.example.org/"));
        GitHubManifest withUrl = new GitHubManifest(configured, credentials, api, JSON, clock);
        assertThat(withUrl.dashboard("http://localhost:4321")).isEqualTo(URI.create("https://bootshift.example.org"));
    }

    @Test
    void tradesTheCodeForTheAppAndSavesItsKey() throws Exception {
        String pem = TestKeys.pkcs1Pem(TestKeys.rsa());
        String state = state(manifest.start(DASHBOARD, null));
        server.expect(requestTo(API + "/app-manifests/code123/conversions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(conversion(77, "bootshift-abc123", pem), MediaType.APPLICATION_JSON));

        GitHubManifest.Connected connected = manifest.complete("code123", state);

        assertThat(connected.installUrl()).isEqualTo("https://github.com/apps/bootshift-abc123/installations/new");
        assertThat(connected.dashboard()).isEqualTo(URI.create(DASHBOARD));
        assertThat(credentials.current()).hasValueSatisfying(app -> {
            assertThat(app.appId()).isEqualTo(77);
            assertThat(app.privateKey()).hasContent(pem);
        });
        server.verify();

        // The app now signs requests with the saved key.
        server.reset();
        server.expect(requestTo(API + "/app/installations?per_page=100"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ey")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        GitHubApp.Status status = new GitHubApp(credentials, api, clock).status();
        assertThat(status.configured()).isTrue();
        assertThat(status.source()).isEqualTo(GitHubCredentials.Source.CONNECTED);
        assertThat(status.appUrl()).isEqualTo("https://github.com/apps/bootshift-abc123");
        assertThat(status.error()).isNull();
    }

    @Test
    void aStateWorksOnlyOnce() {
        String state = state(manifest.start(DASHBOARD, null));
        server.expect(requestTo(API + "/app-manifests/c/conversions"))
                .andRespond(withSuccess(conversion(1, "bootshift-1", "PEM"), MediaType.APPLICATION_JSON));
        manifest.complete("c", state);

        assertThatThrownBy(() -> manifest.complete("c", state)).isInstanceOf(GitHubManifest.InvalidState.class);
    }

    @Test
    void refusesRedirectsItDidNotStartOrThatAreTooOld() {
        assertThatThrownBy(() -> manifest.complete("c", "made-up")).isInstanceOf(GitHubManifest.InvalidState.class);
        assertThatThrownBy(() -> manifest.complete("c", null)).isInstanceOf(GitHubManifest.InvalidState.class);

        String state = state(manifest.start(DASHBOARD, null));
        clock.advance(Duration.ofMinutes(61));
        assertThatThrownBy(() -> manifest.complete("c", state)).isInstanceOf(GitHubManifest.InvalidState.class);
        server.verify();
        assertThat(credentials.current()).isEmpty();
    }

    @Test
    void sendsTheUserBackToTheDashboardWhenGitHubRefusesTheCode() {
        String state = state(manifest.start(DASHBOARD, null));
        server.expect(requestTo(API + "/app-manifests/stale/conversions")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> manifest.complete("stale", state))
                .isInstanceOfSatisfying(GitHubManifest.ConnectFailed.class,
                        e -> assertThat(e.dashboard()).isEqualTo(URI.create(DASHBOARD)));
        assertThatThrownBy(() -> manifest.complete("../../app", state(manifest.start(DASHBOARD, null))))
                .isInstanceOf(GitHubManifest.ConnectFailed.class);
        assertThat(credentials.current()).isEmpty();
    }

    @Test
    void keepsOnlyAFewFlowsOpen() {
        String first = state(manifest.start(DASHBOARD, null));
        for (int i = 0; i < GitHubManifest.MAX_PENDING; i++) {
            clock.advance(Duration.ofSeconds(1));
            manifest.start(DASHBOARD, null);
        }

        assertThatThrownBy(() -> manifest.complete("c", first)).isInstanceOf(GitHubManifest.InvalidState.class);
    }

    @Test
    void cannotReplaceAnAppSetByTheEnvironment() {
        GitHubProperties env = new GitHubProperties(42L, "manual", tmp.resolve("k.pem"), URI.create(API), tmp, null);
        GitHubManifest managed = new GitHubManifest(env, new GitHubCredentials(env, JSON, clock), api, JSON, clock);

        assertThatThrownBy(() -> managed.start(DASHBOARD, null)).isInstanceOf(GitHubCredentials.ManagedByEnvironment.class);
    }

    private GitHubProperties properties(URI dashboardUrl) {
        return new GitHubProperties(null, null, null, URI.create(API), tmp.resolve("home"), dashboardUrl);
    }

    private static String state(GitHubManifest.Form form) {
        return form.action().substring(form.action().indexOf("state=") + "state=".length());
    }

    private static String conversion(long id, String slug, String pem) {
        return JSON.writeValueAsString(java.util.Map.of(
                "id", id, "slug", slug, "name", slug, "html_url", "https://github.com/apps/" + slug,
                "owner", java.util.Map.of("login", "hsanjebri", "type", "User"), "pem", pem,
                "client_id", "Iv1.x", "client_secret", "secret", "webhook_secret", "hook"));
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
