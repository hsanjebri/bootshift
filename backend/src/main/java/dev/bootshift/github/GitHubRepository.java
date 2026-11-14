package dev.bootshift.github;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/** A repository the GitHub App can see, flattened for the rest of Bootshift. */
public record GitHubRepository(
        long githubId,
        long installationId,
        String owner,
        String name,
        String fullName,
        boolean isPrivate,
        boolean archived,
        String defaultBranch,
        String htmlUrl,
        @Nullable Instant pushedAt) {
}
