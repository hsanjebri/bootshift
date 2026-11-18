package dev.bootshift.github;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Which GitHub App Bootshift acts as. Properties ({@code GITHUB_APP_ID} and
 * {@code GITHUB_APP_PRIVATE_KEY_PATH}) win; otherwise the app saved by the Connect GitHub button.
 *
 * <p>A saved app is two files in {@link GitHubProperties#resolvedConfigDir()}: {@code github-app.json}
 * (ID, slug, links) and the private key {@code github-app-<id>.pem}. Both are readable only by the
 * user running Bootshift. Neither is ever stored in the database or sent to the dashboard.
 */
@Service
public class GitHubCredentials {

    static final String CONNECTION_FILE = "github-app.json";

    private static final Logger log = LoggerFactory.getLogger(GitHubCredentials.class);

    public enum Source { ENVIRONMENT, CONNECTED }

    /** The app in use. {@code privateKey} is a path; the key itself is only read to sign JWTs. */
    public record App(long appId, @Nullable String slug, Path privateKey, @Nullable String htmlUrl, Source source) {

        public @Nullable String installUrl() {
            return GitHubProperties.installUrl(slug);
        }
    }

    /** The JSON file's content. */
    record Saved(long appId, String slug, String name, String htmlUrl, String owner, String privateKeyFile,
                 Instant connectedAt) {
    }

    private final GitHubProperties properties;
    private final JsonMapper json;
    private final Clock clock;
    /** null until first read. */
    private final AtomicReference<@Nullable Optional<App>> saved = new AtomicReference<>();

    @Autowired
    public GitHubCredentials(GitHubProperties properties, JsonMapper json) {
        this(properties, json, Clock.systemUTC());
    }

    GitHubCredentials(GitHubProperties properties, JsonMapper json, Clock clock) {
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    public Optional<App> current() {
        if (properties.configured()) {
            return Optional.of(new App(properties.appId(), properties.appSlug(), properties.privateKeyPath(), null,
                    Source.ENVIRONMENT));
        }
        Optional<App> app = saved.get();
        if (app == null) {
            app = load();
            saved.set(app);
        }
        return app;
    }

    public boolean configured() {
        return current().isPresent();
    }

    /** True when the app comes from properties, so the dashboard cannot replace or remove it. */
    public boolean managedByEnvironment() {
        return properties.configured();
    }

    /** Saves an app created with the manifest flow, replacing an earlier saved app. */
    public synchronized App save(GitHubApi.ManifestConversion created) {
        requireNotManagedByEnvironment();
        Path dir = properties.resolvedConfigDir();
        String keyFile = "github-app-" + created.id() + ".pem";
        Optional<App> previous = current();
        try {
            createPrivateDirectory(dir);
            writePrivate(dir.resolve(keyFile), created.pem());
            Saved record = new Saved(created.id(), created.slug(), created.name(), created.htmlUrl(),
                    created.owner().login(), keyFile, Instant.now(clock));
            writePrivate(dir.resolve(CONNECTION_FILE), json.writerWithDefaultPrettyPrinter().writeValueAsString(record));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the GitHub App in " + dir, e);
        }
        previous.filter(p -> p.source() == Source.CONNECTED && !p.privateKey().getFileName().toString().equals(keyFile))
                .ifPresent(p -> deleteQuietly(p.privateKey()));
        App app = new App(created.id(), created.slug(), dir.resolve(keyFile), created.htmlUrl(), Source.CONNECTED);
        saved.set(Optional.of(app));
        log.info("Connected GitHub App {} (id {}), saved in {}", created.slug(), created.id(), dir);
        return app;
    }

    /** Deletes the saved app's files. The app itself stays on GitHub until the owner deletes it there. */
    public synchronized Optional<App> forget() {
        requireNotManagedByEnvironment();
        Optional<App> app = current();
        app.ifPresent(a -> deleteQuietly(a.privateKey()));
        deleteQuietly(properties.resolvedConfigDir().resolve(CONNECTION_FILE));
        saved.set(Optional.empty());
        return app;
    }

    private Optional<App> load() {
        Path dir = properties.resolvedConfigDir();
        Path file = dir.resolve(CONNECTION_FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            Saved record = json.readValue(Files.readString(file), Saved.class);
            Path key = dir.resolve(record.privateKeyFile()).normalize();
            if (!key.getParent().equals(dir.normalize()) || !Files.isRegularFile(key)) {
                log.warn("Ignoring {}: its private key file is missing or outside {}", file, dir);
                return Optional.empty();
            }
            return Optional.of(new App(record.appId(), record.slug(), key, record.htmlUrl(), Source.CONNECTED));
        } catch (IOException | JacksonException e) {
            log.warn("Ignoring unreadable {}: {}", file, e.getMessage());
            return Optional.empty();
        }
    }

    private void requireNotManagedByEnvironment() {
        if (properties.configured()) {
            throw new ManagedByEnvironment();
        }
    }

    private static void createPrivateDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            return;
        }
        Files.createDirectories(dir);
        PosixFileAttributeView posix = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rwx------"));
        }
    }

    /** Writes to a temporary file that only the owner can read, then moves it into place. */
    static void writePrivate(Path target, String content) throws IOException {
        Path tmp = Files.createTempFile(target.getParent(), ".github-app", ".tmp");
        try {
            restrictToOwner(tmp);
            Files.writeString(tmp, content);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** rw------- on POSIX; on Windows an ACL with only the owner. */
    static void restrictToOwner(Path file) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            acl.setAcl(List.of(AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                    .build()));
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", file, e.getMessage());
        }
    }

    public static final class ManagedByEnvironment extends RuntimeException {
        public ManagedByEnvironment() {
            super("The GitHub App is set with GITHUB_APP_ID and GITHUB_APP_PRIVATE_KEY_PATH. Change those instead.");
        }
    }
}
