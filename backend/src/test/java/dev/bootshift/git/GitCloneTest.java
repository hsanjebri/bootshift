package dev.bootshift.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCloneTest {

    @TempDir
    Path tmp;

    Path origin;
    String mainSha;
    String featureSha;
    final GitClone clone = new GitClone(Duration.ofSeconds(30));

    @BeforeEach
    void createOrigin() throws Exception {
        origin = tmp.resolve("origin");
        try (Git git = Git.init().setDirectory(origin.toFile()).setInitialBranch("main").call()) {
            Files.writeString(origin.resolve("pom.xml"), "<project/>");
            git.add().addFilepattern("pom.xml").call();
            mainSha = git.commit().setMessage("init").setAuthor("t", "t@example.com").setSign(false).call().name();
            git.checkout().setCreateBranch(true).setName("feature").call();
            Files.writeString(origin.resolve("README.md"), "feature");
            git.add().addFilepattern("README.md").call();
            featureSha = git.commit().setMessage("feature").setAuthor("t", "t@example.com").setSign(false).call().name();
            git.checkout().setName("main").call();
        }
    }

    @Test
    void clonesTheDefaultBranchAndCreatesTheBootshiftBranch() throws Exception {
        Path target = tmp.resolve("work");

        GitClone.Cloned cloned = clone.clone(origin.toUri(), null, null, target);

        assertThat(cloned).isEqualTo(new GitClone.Cloned("main", mainSha));
        try (Git git = Git.open(target.toFile())) {
            assertThat(git.getRepository().getBranch()).isEqualTo(GitClone.WORK_BRANCH);
            assertThat(git.getRepository().resolve("HEAD")).isEqualTo(ObjectId.fromString(mainSha));
        }
        assertThat(target.resolve("pom.xml")).exists();
    }

    @Test
    void clonesARequestedBranch() {
        GitClone.Cloned cloned = clone.clone(origin.toUri(), "feature", null, tmp.resolve("work"));

        assertThat(cloned).isEqualTo(new GitClone.Cloned("feature", featureSha));
        assertThat(tmp.resolve("work/README.md")).hasContent("feature");
    }

    @Test
    void neverLeavesTheTokenInTheWorkingCopy() throws Exception {
        Path target = tmp.resolve("work");
        // A local remote ignores credentials, which is enough to check nothing is written to disk.
        clone.clone(origin.toUri(), null, "ghs_secretToken123", target);

        assertThat(Files.readString(target.resolve(".git/config"))).doesNotContain("ghs_secretToken123");
    }

    @Test
    void refusesAWorkingCopyThatContainsTheToken() throws Exception {
        Path work = tmp.resolve("work");
        Files.createDirectories(work.resolve(".git"));
        Files.writeString(work.resolve(".git/config"), "[remote \"origin\"]\n\turl = https://x-access-token:ghs_abc@github.com/a/b.git\n");

        assertThatThrownBy(() -> GitClone.requireNoToken(work, "ghs_abc"))
                .isInstanceOf(GitClone.CloneFailed.class)
                .hasMessageContaining("refusing");
    }

    @Test
    void rejectsCredentialsInTheUrlAndPlainHttp() {
        assertThatThrownBy(() -> clone.clone(URI.create("https://user:pw@github.com/a/b.git"), null, null, tmp.resolve("w")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> clone.clone(URI.create("http://github.com/a/b.git"), null, null, tmp.resolve("w")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redactsTheTokenFromErrors() {
        assertThat(GitClone.redact("auth failed for ghs_abc at host", "ghs_abc")).isEqualTo("auth failed for *** at host");
    }

    @Test
    void reportsAMissingBranchAsACloneFailure() {
        assertThatThrownBy(() -> clone.clone(origin.toUri(), "nope", null, tmp.resolve("work")))
                .isInstanceOf(GitClone.CloneFailed.class);
    }
}
