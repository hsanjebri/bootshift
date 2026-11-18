package dev.bootshift.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.json.JsonMapper;

/** Where the Connect GitHub button keeps the app, and how properties override it. */
class GitHubCredentialsTest {

    private static final URI API = URI.create("https://api.github.com");

    @TempDir
    Path tmp;

    @Test
    void nothingIsConfiguredUntilAnAppIsSaved() {
        GitHubCredentials credentials = credentials(home());

        assertThat(credentials.current()).isEmpty();
        assertThat(credentials.configured()).isFalse();
    }

    @Test
    void savesTheAppSoAFreshStartFindsIt() throws Exception {
        GitHubCredentials.App app = credentials(home()).save(created(101, "bootshift-a1b2c3", "PEM-101"));

        assertThat(app.source()).isEqualTo(GitHubCredentials.Source.CONNECTED);
        assertThat(app.installUrl()).isEqualTo("https://github.com/apps/bootshift-a1b2c3/installations/new");
        assertThat(app.privateKey()).hasFileName("github-app-101.pem").hasContent("PEM-101");

        GitHubCredentials restarted = credentials(home());
        assertThat(restarted.current()).contains(app);
        // The key lives only in its own file, never in the JSON next to it.
        assertThat(Files.readString(home().resolve(GitHubCredentials.CONNECTION_FILE)))
                .containsPattern("\"appId\"[ :]+101").contains("bootshift-a1b2c3")
                .doesNotContain("PEM-101");
    }

    @Test
    void onlyTheOwnerCanReadTheSavedFiles() throws Exception {
        GitHubCredentials.App app = credentials(home()).save(created(7, "bootshift-x", "PEM"));

        for (Path file : new Path[] {app.privateKey(), home().resolve(GitHubCredentials.CONNECTION_FILE)}) {
            PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
            if (posix != null) {
                assertThat(PosixFilePermissions.toString(posix.readAttributes().permissions())).isEqualTo("rw-------");
            } else {
                AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
                assertThat(acl.getAcl()).singleElement()
                        .satisfies(entry -> assertThat(entry.principal()).isEqualTo(acl.getOwner()));
            }
        }
        try (var files = Files.list(home())) {
            assertThat(files.map(f -> f.getFileName().toString())).noneMatch(n -> n.endsWith(".tmp"));
        }
    }

    @Test
    void reconnectingReplacesTheOldKey() {
        GitHubCredentials credentials = credentials(home());
        GitHubCredentials.App first = credentials.save(created(1, "bootshift-one", "PEM-1"));

        GitHubCredentials.App second = credentials.save(created(2, "bootshift-two", "PEM-2"));

        assertThat(first.privateKey()).doesNotExist();
        assertThat(second.privateKey()).hasContent("PEM-2");
        assertThat(credentials(home()).current()).contains(second);
    }

    @Test
    void forgettingDeletesTheSavedFiles() {
        GitHubCredentials credentials = credentials(home());
        GitHubCredentials.App app = credentials.save(created(3, "bootshift-three", "PEM-3"));

        assertThat(credentials.forget()).contains(app);

        assertThat(credentials.current()).isEmpty();
        assertThat(app.privateKey()).doesNotExist();
        assertThat(home().resolve(GitHubCredentials.CONNECTION_FILE)).doesNotExist();
    }

    @Test
    void ignoresAConnectionFileThatPointsOutsideItsFolder() throws Exception {
        Files.createDirectories(home());
        Files.writeString(tmp.resolve("elsewhere.pem"), "PEM");
        Files.writeString(home().resolve(GitHubCredentials.CONNECTION_FILE), """
                {"appId":9,"slug":"s","name":"n","htmlUrl":"https://github.com/apps/s","owner":"o",
                 "privateKeyFile":"../elsewhere.pem","connectedAt":"2026-11-18T10:00:00Z"}""");

        assertThat(credentials(home()).current()).isEmpty();
    }

    @Test
    void propertiesWinAndCannotBeChangedFromTheDashboard() {
        credentials(home()).save(created(5, "bootshift-saved", "PEM"));
        Path key = tmp.resolve("manual.pem");
        GitHubCredentials credentials = new GitHubCredentials(
                new GitHubProperties(42L, "bootshift-manual", key, API, home(), null), JsonMapper.builder().build());

        assertThat(credentials.current()).hasValueSatisfying(app -> {
            assertThat(app.appId()).isEqualTo(42L);
            assertThat(app.source()).isEqualTo(GitHubCredentials.Source.ENVIRONMENT);
        });
        assertThat(credentials.managedByEnvironment()).isTrue();
        assertThatThrownBy(() -> credentials.save(created(6, "x", "PEM")))
                .isInstanceOf(GitHubCredentials.ManagedByEnvironment.class);
        assertThatThrownBy(credentials::forget).isInstanceOf(GitHubCredentials.ManagedByEnvironment.class);
    }

    @Test
    void neverPrintsTheKey() {
        assertThat(created(1, "s", "-----BEGIN RSA PRIVATE KEY-----").toString()).doesNotContain("BEGIN");
    }

    private Path home() {
        return tmp.resolve("home");
    }

    private static GitHubCredentials credentials(Path home) {
        return new GitHubCredentials(new GitHubProperties(null, null, null, API, home, null), JsonMapper.builder().build());
    }

    static GitHubApi.ManifestConversion created(long id, String slug, String pem) {
        return new GitHubApi.ManifestConversion(id, slug, slug, "https://github.com/apps/" + slug,
                new GitHubApi.Account("hsanjebri", "User"), pem);
    }
}
