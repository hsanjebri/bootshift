package dev.bootshift.run;

import org.jspecify.annotations.Nullable;

import dev.bootshift.repo.RepositoryName;

/** Command to start a run. {@code branch} is the base branch; null means the repository default. */
public record StartRun(RepositoryName repository, @Nullable String branch) {
}
