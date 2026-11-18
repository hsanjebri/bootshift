package dev.bootshift.github;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Talks to GitHub as the configured GitHub App: lists installations and the repositories they can
 * see. Installation tokens are cached until five minutes before they expire. Tokens never leave
 * this class.
 */
@Service
public class GitHubApp {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 20;
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    private final GitHubCredentials credentials;
    private final GitHubApi api;
    private final Clock clock;
    private final Map<Long, GitHubApi.AccessToken> tokens = new ConcurrentHashMap<>();
    /** The signer and the app it was made for; replaced when the app changes. */
    private volatile @Nullable Signer signer;

    private record Signer(GitHubCredentials.App app, GitHubAppJwt jwt) {
    }

    @Autowired
    public GitHubApp(GitHubCredentials credentials, GitHubApi api) {
        this(credentials, api, Clock.systemUTC());
    }

    GitHubApp(GitHubCredentials credentials, GitHubApi api, Clock clock) {
        this.credentials = credentials;
        this.api = api;
        this.clock = clock;
    }

    public record InstallationView(long id, String account, String accountType, String repositorySelection) {
    }

    /**
     * What the dashboard shows in Settings. {@code source} is {@code ENVIRONMENT} for an app set
     * with properties (the dashboard cannot change it) and {@code CONNECTED} for one made with the
     * Connect GitHub button. {@code error} is set when GitHub could not be reached.
     */
    public record Status(boolean configured, GitHubCredentials.@Nullable Source source, @Nullable String appSlug,
                         @Nullable String installUrl, @Nullable String appUrl, List<InstallationView> installations,
                         @Nullable String error) {
    }

    public Status status() {
        Optional<GitHubCredentials.App> current = credentials.current();
        if (current.isEmpty()) {
            return new Status(false, null, null, null, null, List.of(), null);
        }
        GitHubCredentials.App app = current.get();
        try {
            List<InstallationView> installations = api.installations(appBearer(), PAGE_SIZE).stream()
                    .map(i -> new InstallationView(i.id(), i.account().login(), i.account().type(), i.repositorySelection()))
                    .toList();
            return new Status(true, app.source(), app.slug(), app.installUrl(), app.htmlUrl(), installations, null);
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            return new Status(true, app.source(), app.slug(), app.installUrl(), app.htmlUrl(), List.of(), describe(e));
        }
    }

    /** Every repository visible to every installation of the app, sorted by full name. */
    public List<GitHubRepository> repositories() {
        requireConfigured();
        try {
            List<GitHubRepository> all = new ArrayList<>();
            for (GitHubApi.Installation installation : api.installations(appBearer(), PAGE_SIZE)) {
                String token = "Bearer " + installationToken(installation.id());
                for (int page = 1; page <= MAX_PAGES; page++) {
                    GitHubApi.InstallationRepositories result = api.repositories(token, PAGE_SIZE, page);
                    result.repositories().forEach(r -> all.add(toRepository(installation.id(), r)));
                    if (result.repositories().size() < PAGE_SIZE) {
                        break;
                    }
                }
            }
            all.sort((a, b) -> a.fullName().compareToIgnoreCase(b.fullName()));
            return all;
        } catch (RestClientException e) {
            throw new GitHubUnavailable(describe(e), e);
        }
    }

    /** The repository if the app can see it. Case-insensitive, like GitHub. */
    public Optional<GitHubRepository> find(String owner, String name) {
        return repositories().stream()
                .filter(r -> r.owner().equalsIgnoreCase(owner) && r.name().equalsIgnoreCase(name))
                .findFirst();
    }

    /** True when an app is set with properties or saved by the Connect GitHub button. */
    public boolean configured() {
        return credentials.configured();
    }

    /**
     * A fresh token that can only read the contents of one repository, for cloning (README section
     * 13). It is not cached and expires within an hour; callers must not store it.
     *
     * @throws GitHubNotConfigured if no app is configured
     * @throws RepositoryNotVisible if the app is not installed on the repository
     */
    public String cloneToken(String owner, String name) {
        GitHubRepository repository = find(owner, name).orElseThrow(() -> new RepositoryNotVisible(owner + "/" + name));
        try {
            return api.createScopedToken(appBearer(), repository.installationId(),
                    new GitHubApi.TokenScope(List.of(repository.name()), Map.of("contents", "read"))).token();
        } catch (RestClientException e) {
            throw new GitHubUnavailable(describe(e), e);
        }
    }

    String installationToken(long installationId) {
        GitHubApi.AccessToken cached = tokens.get(installationId);
        if (cached != null && Instant.now(clock).isBefore(cached.expiresAt().minus(TOKEN_MARGIN))) {
            return cached.token();
        }
        GitHubApi.AccessToken fresh = api.createInstallationToken(appBearer(), installationId);
        tokens.put(installationId, fresh);
        return fresh.token();
    }

    private String appBearer() {
        GitHubCredentials.App app = credentials.current().orElseThrow(GitHubNotConfigured::new);
        Signer current = signer;
        if (current == null || !current.app().equals(app)) {
            tokens.clear();
            current = new Signer(app, GitHubAppJwt.fromPemFile(app.appId(), app.privateKey(), clock));
            signer = current;
        }
        return "Bearer " + current.jwt().create();
    }

    private void requireConfigured() {
        if (!credentials.configured()) {
            throw new GitHubNotConfigured();
        }
    }

    private static GitHubRepository toRepository(long installationId, GitHubApi.Repo r) {
        return new GitHubRepository(r.id(), installationId, r.owner().login(), r.name(), r.fullName(),
                r.isPrivate(), r.archived(), r.defaultBranch(), r.htmlUrl(), r.pushedAt());
    }

    /** A short reason without request details or tokens. */
    private static String describe(Exception e) {
        String type = e.getClass().getSimpleName();
        String message = e.getMessage();
        if (message == null) {
            return type;
        }
        String firstLine = message.lines().findFirst().orElse("");
        return type + ": " + (firstLine.length() > 200 ? firstLine.substring(0, 200) + "…" : firstLine);
    }

    public static final class GitHubNotConfigured extends RuntimeException {
        public GitHubNotConfigured() {
            super("GitHub is not connected. Use Connect GitHub in Settings, or set GITHUB_APP_ID and GITHUB_APP_PRIVATE_KEY_PATH.");
        }
    }

    public static final class RepositoryNotVisible extends RuntimeException {
        public RepositoryNotVisible(String repository) {
            super("The GitHub App is not installed on " + repository);
        }
    }

    public static final class GitHubUnavailable extends RuntimeException {
        public GitHubUnavailable(String message, Throwable cause) {
            super("GitHub request failed: " + message, cause);
        }
    }
}
