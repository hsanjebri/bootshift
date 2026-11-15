package dev.bootshift.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.bootshift.build.BuildResultStore;
import dev.bootshift.repo.RepositoryName;
import dev.bootshift.run.Run;
import dev.bootshift.run.RunService;
import dev.bootshift.run.StartRun;

@RestController
@RequestMapping("/api/{version}/runs")
class RunController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";
    private static final int MAX_KEY_LENGTH = 200;

    private final RunService runs;
    private final BuildResultStore builds;

    RunController(RunService runs, BuildResultStore builds) {
        this.runs = runs;
        this.builds = builds;
    }

    @PostMapping(version = ApiVersionConfig.V1)
    ResponseEntity<Run> start(@Valid @RequestBody StartRunRequest request,
                              @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > MAX_KEY_LENGTH)) {
            throw new InvalidRequest(IDEMPOTENCY_KEY + " must be 1-" + MAX_KEY_LENGTH + " characters");
        }
        var started = runs.start(new StartRun(RepositoryName.parse(request.repository()), request.branch()), idempotencyKey);
        Run run = started.run();
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/runs/" + run.id()))
                .header(IDEMPOTENT_REPLAYED, String.valueOf(!started.created()))
                .body(run);
    }

    @GetMapping(version = ApiVersionConfig.V1)
    List<Run> list(@RequestParam(defaultValue = "50") int limit) {
        return runs.list(limit);
    }

    @GetMapping(path = "/{id}", version = ApiVersionConfig.V1)
    Run get(@PathVariable UUID id) {
        return runs.get(id);
    }

    /** The run's sandbox builds, oldest first, with their failures. */
    @GetMapping(path = "/{id}/builds", version = ApiVersionConfig.V1)
    List<BuildResultStore.StoredBuild> builds(@PathVariable UUID id) {
        runs.get(id);
        return builds.list(id);
    }

    @PostMapping(path = "/{id}/cancel", version = ApiVersionConfig.V1)
    ResponseEntity<Run> cancel(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(runs.cancel(id));
    }

    static final class InvalidRequest extends RuntimeException {
        InvalidRequest(String message) {
            super(message);
        }
    }
}
