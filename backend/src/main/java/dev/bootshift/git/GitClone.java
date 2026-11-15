package dev.bootshift.git;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jspecify.annotations.Nullable;

/**
 * Clones a repository into an empty directory and checks out the Bootshift branch.
 *
 * <p>The token is passed to JGit as a credentials provider, never in the URL, so it is not stored
 * in {@code .git/config}. {@link #clone} still checks the config afterwards and refuses the working
 * copy if the token appears anywhere in it.
 */
public class GitClone {

    /** The only branch Bootshift ever pushes (README section 13). */
    public static final String WORK_BRANCH = "bootshift/spring-boot-4";

    private final Duration timeout;

    public GitClone(Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * @param baseBranch the branch the run starts from
     * @param sourceSha  the commit at the tip of that branch
     */
    public record Cloned(String baseBranch, String sourceSha) {
    }

    /**
     * @param remote an {@code https} URL (or {@code file} in tests) without credentials
     * @param branch the base branch, or null for the remote's default branch
     * @param token  a short-lived token with read access, or null for a public repository
     */
    public Cloned clone(URI remote, @Nullable String branch, @Nullable String token, Path target) {
        if (!"https".equals(remote.getScheme()) && !"file".equals(remote.getScheme())) {
            throw new IllegalArgumentException("Only https remotes are supported: " + remote);
        }
        if (remote.getUserInfo() != null) {
            throw new IllegalArgumentException("Credentials must not be part of the remote URL");
        }
        CloneCommand command = Git.cloneRepository()
                .setURI(remote.toString())
                .setDirectory(target.toFile())
                .setDepth(1)
                .setCloneSubmodules(false)
                .setTimeout((int) timeout.toSeconds());
        if (branch != null) {
            command.setBranch(branch).setBranchesToClone(List.of("refs/heads/" + branch));
        }
        if (token != null) {
            command.setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", token));
        }
        try (Git git = command.call()) {
            String base = git.getRepository().getBranch();
            ObjectId head = git.getRepository().resolve("HEAD");
            if (head == null) {
                throw new CloneFailed("The repository is empty");
            }
            git.checkout().setCreateBranch(true).setName(WORK_BRANCH).call();
            Cloned cloned = new Cloned(base, head.name());
            if (token != null) {
                requireNoToken(target, token);
            }
            return cloned;
        } catch (GitAPIException | IOException e) {
            throw new CloneFailed(redact(e.getMessage(), token), e);
        }
    }

    /** README section 13: credentials are removed before any container sees the working copy. */
    static void requireNoToken(Path workingCopy, String token) throws IOException {
        Path config = workingCopy.resolve(".git").resolve("config");
        if (Files.exists(config) && Files.readString(config, StandardCharsets.UTF_8).contains(token)) {
            throw new CloneFailed("The clone stored credentials in .git/config; refusing to use it");
        }
        Path credentials = workingCopy.resolve(".git-credentials");
        if (Files.exists(credentials)) {
            throw new CloneFailed("The working copy contains a .git-credentials file; refusing to use it");
        }
    }

    static String redact(@Nullable String message, @Nullable String token) {
        if (message == null) {
            return "Clone failed";
        }
        return token == null || token.isEmpty() ? message : message.replace(token, "***");
    }

    public static final class CloneFailed extends RuntimeException {
        public CloneFailed(String message) {
            super(message);
        }

        public CloneFailed(String message, Throwable cause) {
            super("Clone failed: " + message, cause);
        }
    }
}
