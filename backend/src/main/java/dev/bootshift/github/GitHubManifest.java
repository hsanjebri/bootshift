package dev.bootshift.github;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.json.JsonMapper;

/**
 * The Connect GitHub button, using GitHub's app manifest flow: Bootshift describes the app, the
 * user confirms it on GitHub, and GitHub redirects back with a one-hour code that Bootshift trades
 * for the app's ID and private key. The user never copies a key by hand.
 *
 * <p>The app gets the permissions from README section 18, is private to its owner, and has no
 * webhook. Each flow carries a random single-use {@code state}, so a redirect that Bootshift did
 * not start is refused.
 */
@Service
public class GitHubManifest {

    static final String CALLBACK_PATH = "/api/v1/github/manifest/callback";
    static final String SETUP_PATH = "/repositories?installed=1";
    static final Duration STATE_TTL = Duration.ofHours(1);
    static final int MAX_PENDING = 20;

    private static final Logger log = LoggerFactory.getLogger(GitHubManifest.class);
    private static final Pattern LOGIN = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    /** What the dashboard posts to GitHub: the form's action URL and the {@code manifest} field. */
    public record Form(String action, String manifest) {
    }

    /** The app was created and saved; the user installs it next. */
    public record Connected(String slug, @Nullable String installUrl, URI dashboard) {
    }

    private record Pending(URI dashboard, Instant expiresAt) {
    }

    private final GitHubProperties properties;
    private final GitHubCredentials credentials;
    private final GitHubApi api;
    private final JsonMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    @Autowired
    public GitHubManifest(GitHubProperties properties, GitHubCredentials credentials, GitHubApi api, JsonMapper json) {
        this(properties, credentials, api, json, Clock.systemUTC());
    }

    GitHubManifest(GitHubProperties properties, GitHubCredentials credentials, GitHubApi api, JsonMapper json,
                   Clock clock) {
        this.properties = properties;
        this.credentials = credentials;
        this.api = api;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Starts a flow.
     *
     * @param requestedDashboard the dashboard's origin as the browser sees it; ignored when
     *                           {@code bootshift.github.dashboard-url} is set
     * @param organization       create the app under this organization instead of the user's account
     */
    public Form start(@Nullable String requestedDashboard, @Nullable String organization) {
        if (credentials.managedByEnvironment()) {
            throw new GitHubCredentials.ManagedByEnvironment();
        }
        URI dashboard = dashboard(requestedDashboard);
        String org = organization == null || organization.isBlank() ? null : organization.strip();
        if (org != null && !LOGIN.matcher(org).matches()) {
            throw new InvalidRequest("Not a GitHub organization name: " + org);
        }

        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(32));
        remember(state, dashboard);

        String base = webUrl() + (org == null ? "/settings/apps/new" : "/organizations/" + org + "/settings/apps/new");
        return new Form(base + "?state=" + state, manifest(dashboard));
    }

    /**
     * Trades the code from GitHub's redirect for the new app and saves it.
     *
     * @throws InvalidState  if Bootshift did not start this flow, or it is older than an hour
     * @throws ConnectFailed if GitHub refused the code; carries the dashboard to send the user back to
     */
    public Connected complete(@Nullable String code, @Nullable String state) {
        Pending flow = state == null ? null : pending.remove(state);
        if (flow == null || Instant.now(clock).isAfter(flow.expiresAt())) {
            throw new InvalidState();
        }
        if (code == null || !CODE.matcher(code).matches()) {
            throw new ConnectFailed(flow.dashboard(), "GitHub did not send a valid code");
        }
        GitHubApi.ManifestConversion created;
        try {
            created = api.convertManifest(code);
        } catch (RestClientException e) {
            log.warn("GitHub refused the app manifest code: {}", e.getMessage());
            throw new ConnectFailed(flow.dashboard(), "GitHub refused the code: " + e.getClass().getSimpleName());
        }
        GitHubCredentials.App app = credentials.save(created);
        return new Connected(created.slug(), app.installUrl(), flow.dashboard());
    }

    String manifest(URI dashboard) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", "bootshift-" + HexFormat.of().formatHex(bytes(3)));
        manifest.put("url", dashboard.toString());
        manifest.put("description", "Self-hosted Bootshift: upgrades Spring Boot projects and opens pull requests.");
        manifest.put("public", false);
        manifest.put("redirect_url", dashboard + CALLBACK_PATH);
        manifest.put("setup_url", dashboard + SETUP_PATH);
        manifest.put("setup_on_update", true);
        manifest.put("hook_attributes", Map.of("url", dashboard.toString(), "active", false));
        manifest.put("default_permissions", Map.of("contents", "write", "pull_requests", "write", "metadata", "read"));
        return json.writeValueAsString(manifest);
    }

    /** {@code bootshift.github.dashboard-url}, or a localhost origin sent by the dashboard. */
    URI dashboard(@Nullable String requested) {
        if (properties.dashboardUrl() != null) {
            return origin(properties.dashboardUrl());
        }
        if (requested == null || requested.isBlank()) {
            throw new InvalidRequest("The dashboard address is missing.");
        }
        URI uri;
        try {
            uri = new URI(requested.strip());
        } catch (URISyntaxException e) {
            throw new InvalidRequest("Not a URL: " + requested);
        }
        boolean http = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        boolean bare = uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                && (uri.getPath() == null || uri.getPath().isEmpty() || uri.getPath().equals("/"));
        if (!http || !bare || uri.getHost() == null || !LOCAL_HOSTS.contains(uri.getHost().toLowerCase())) {
            throw new InvalidRequest("Only a localhost dashboard can connect GitHub unless bootshift.github.dashboard-url"
                    + " (BOOTSHIFT_DASHBOARD_URL) is set.");
        }
        return origin(uri);
    }

    private static URI origin(URI uri) {
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return URI.create(uri.getScheme() + "://" + uri.getHost() + port);
    }

    /** github.com for api.github.com; the server itself for GitHub Enterprise ({@code https://host/api/v3}). */
    private String webUrl() {
        URI api = properties.baseUrl();
        return "api.github.com".equals(api.getHost()) ? "https://github.com" : origin(api).toString();
    }

    private void remember(String state, URI dashboard) {
        Instant now = Instant.now(clock);
        pending.values().removeIf(p -> now.isAfter(p.expiresAt()));
        while (pending.size() >= MAX_PENDING) {
            pending.entrySet().stream().min(Comparator.comparing(e -> e.getValue().expiresAt()))
                    .ifPresent(e -> pending.remove(e.getKey()));
        }
        pending.put(state, new Pending(dashboard, now.plus(STATE_TTL)));
    }

    private byte[] bytes(int count) {
        byte[] bytes = new byte[count];
        random.nextBytes(bytes);
        return bytes;
    }

    public static final class InvalidRequest extends RuntimeException {
        public InvalidRequest(String message) {
            super(message);
        }
    }

    public static final class InvalidState extends RuntimeException {
        public InvalidState() {
            super("This GitHub redirect was not started by Bootshift or is more than an hour old. Click Connect GitHub again.");
        }
    }

    public static final class ConnectFailed extends RuntimeException {
        private final URI dashboard;

        public ConnectFailed(URI dashboard, String message) {
            super(message);
            this.dashboard = dashboard;
        }

        public URI dashboard() {
            return dashboard;
        }
    }
}
