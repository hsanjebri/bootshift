package dev.bootshift.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.bootshift.github.GitHubApp;
import dev.bootshift.run.RunExceptions.IdempotencyKeyReused;
import dev.bootshift.run.RunExceptions.RepositoryNotAllowed;
import dev.bootshift.run.RunExceptions.RunAlreadyFinished;
import dev.bootshift.run.RunExceptions.RunNotFound;

/**
 * Maps domain errors to RFC 9457 Problem Details. Framework errors (validation, bad UUIDs,
 * unsupported API versions) are handled by Boot's built-in problem-details support.
 */
@RestControllerAdvice
class ProblemHandler {

    private static final String TYPE_BASE = "https://bootshift.dev/problems/";

    @ExceptionHandler
    ProblemDetail notFound(RunNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "run-not-found", "Run not found", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail finished(RunAlreadyFinished e) {
        return problem(HttpStatus.CONFLICT, "run-finished", "Run already finished", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail keyReused(IdempotencyKeyReused e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "idempotency-key-reused", "Idempotency-Key reused", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail invalid(RunController.InvalidRequest e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notAllowed(RepositoryNotAllowed e) {
        return problem(HttpStatus.FORBIDDEN, "repository-not-allowed", "Repository not allowed", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notVisible(GitHubController.RepositoryNotVisible e) {
        return problem(HttpStatus.NOT_FOUND, "repository-not-visible", "Repository not visible to the GitHub App", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail archived(GitHubController.RepositoryArchived e) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "repository-archived", "Repository archived", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail githubNotConfigured(GitHubApp.GitHubNotConfigured e) {
        return problem(HttpStatus.CONFLICT, "github-not-configured", "GitHub is not connected", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail githubUnavailable(GitHubApp.GitHubUnavailable e) {
        return problem(HttpStatus.BAD_GATEWAY, "github-unavailable", "GitHub request failed", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
