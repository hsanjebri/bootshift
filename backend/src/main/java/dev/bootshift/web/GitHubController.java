package dev.bootshift.web;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.bootshift.github.GitHubApp;
import dev.bootshift.github.GitHubRepository;
import dev.bootshift.repo.RepositoryName;
import dev.bootshift.repo.RepositoryService;
import dev.bootshift.repo.RepositoryService.AllowedRepository;

/**
 * GitHub connection status, the repositories the GitHub App can see, and the allowlist
 * (README section 10: {@code /api/v1/repositories}).
 */
@RestController
@RequestMapping("/api/{version}")
class GitHubController {

    private final GitHubApp github;
    private final RepositoryService repositories;

    GitHubController(GitHubApp github, RepositoryService repositories) {
        this.github = github;
        this.repositories = repositories;
    }

    /** A repository the app can see, with whether it is allowlisted. */
    record RepositoryView(String owner, String name, String fullName, boolean isPrivate, boolean archived,
                          String defaultBranch, String htmlUrl, @Nullable Instant pushedAt, boolean allowed) {
    }

    record AllowRequest(@NotNull Boolean allowed) {
    }

    @GetMapping(path = "/github/status", version = ApiVersionConfig.V1)
    GitHubApp.Status status() {
        return github.status();
    }

    @GetMapping(path = "/github/repositories", version = ApiVersionConfig.V1)
    List<RepositoryView> visibleRepositories() {
        Set<String> allowed = repositories.allowed().stream()
                .map(r -> r.fullName().toLowerCase())
                .collect(Collectors.toSet());
        return github.repositories().stream()
                .map(r -> view(r, allowed.contains(r.fullName().toLowerCase())))
                .toList();
    }

    @GetMapping(path = "/repositories", version = ApiVersionConfig.V1)
    List<AllowedRepository> allowlist() {
        return repositories.allowed();
    }

    /** Adds or removes a repository from the allowlist. Adding requires the GitHub App to see it. */
    @PutMapping(path = "/repositories/{owner}/{name}", version = ApiVersionConfig.V1)
    RepositoryView setAllowed(@PathVariable String owner, @PathVariable String name,
                              @Valid @RequestBody AllowRequest request) {
        RepositoryName requested;
        try {
            requested = new RepositoryName(owner, name);
        } catch (IllegalArgumentException e) {
            throw new RunController.InvalidRequest(e.getMessage());
        }
        GitHubRepository repo = github.find(requested.owner(), requested.name())
                .orElseThrow(() -> new RepositoryNotVisible(requested.toString()));
        RepositoryName canonical = new RepositoryName(repo.owner(), repo.name());
        if (request.allowed()) {
            if (repo.archived()) {
                throw new RepositoryArchived(repo.fullName());
            }
            repositories.allow(canonical, new RepositoryService.GitHubDetails(
                    repo.githubId(), repo.installationId(), repo.isPrivate(), repo.htmlUrl()));
        } else {
            repositories.disallow(canonical);
        }
        return view(repo, request.allowed());
    }

    private static RepositoryView view(GitHubRepository r, boolean allowed) {
        return new RepositoryView(r.owner(), r.name(), r.fullName(), r.isPrivate(), r.archived(), r.defaultBranch(),
                r.htmlUrl(), r.pushedAt(), allowed);
    }

    static final class RepositoryNotVisible extends RuntimeException {
        RepositoryNotVisible(String repository) {
            super("The GitHub App cannot see " + repository + ". Install the app on it first.");
        }
    }

    static final class RepositoryArchived extends RuntimeException {
        RepositoryArchived(String repository) {
            super(repository + " is archived, so Bootshift could not open a pull request on it.");
        }
    }
}
