package dev.bootshift.run;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** A migration run as stored in the {@code run} table, joined with its repository name. */
public record Run(
        UUID id,
        String repository,
        RunState status,
        @Nullable RunState stage,
        @Nullable String branch,
        @Nullable String prUrl,
        boolean draft,
        @Nullable String failureReason,
        boolean cancelRequested,
        Instant createdAt,
        @Nullable Instant startedAt,
        @Nullable Instant finishedAt) {
}
