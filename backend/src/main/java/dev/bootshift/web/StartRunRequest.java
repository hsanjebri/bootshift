package dev.bootshift.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.jspecify.annotations.Nullable;

import dev.bootshift.repo.RepositoryName;

/**
 * Body of {@code POST /api/v1/runs}.
 *
 * @param repository GitHub {@code owner/name}
 * @param branch     base branch; omitted means the repository default
 */
record StartRunRequest(
        @NotBlank @Pattern(regexp = RepositoryName.PATTERN, message = "must be a GitHub owner/name") String repository,
        @Nullable @Pattern(regexp = BRANCH, message = "must be a valid branch name") String branch) {

    /** Letters, digits, '.', '_', '/', '-'; no "..", no leading '-' or '/'. */
    static final String BRANCH = "^(?!.*\\.\\.)(?![-/])[A-Za-z0-9._/-]{1,255}$";
}
