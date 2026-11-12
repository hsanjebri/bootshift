package dev.bootshift.web;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import dev.bootshift.run.RunEvent;
import dev.bootshift.run.RunEventSignal;
import dev.bootshift.run.RunService;

/**
 * Live run events over Server-Sent Events. The stream replays every event after
 * {@code Last-Event-ID} (all of them on first connect), then follows new ones, and completes
 * after the event that ends the run.
 */
@RestController
@RequestMapping("/api/{version}/runs")
class RunEventsController {

    private static final Logger log = LoggerFactory.getLogger(RunEventsController.class);
    private static final int BATCH = 200;
    private static final Duration HEARTBEAT = Duration.ofSeconds(15);
    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);

    private final RunService runs;
    private final RunEventSignal signal;

    RunEventsController(RunService runs, RunEventSignal signal) {
        this.runs = runs;
        this.signal = signal;
    }

    @GetMapping(path = "/{id}/events", version = ApiVersionConfig.V1, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@PathVariable UUID id,
                      @RequestHeader(name = "Last-Event-ID", required = false) @Nullable Long lastEventId) {
        runs.get(id); // 404 before the stream opens
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        AtomicBoolean open = new AtomicBoolean(true);
        emitter.onCompletion(() -> open.set(false));
        emitter.onTimeout(() -> open.set(false));
        emitter.onError(e -> open.set(false));
        long start = lastEventId == null ? 0 : lastEventId;
        Thread.ofVirtual().name("bootshift-sse-" + id).start(() -> stream(id, start, emitter, open));
        return emitter;
    }

    private void stream(UUID id, long afterId, SseEmitter emitter, AtomicBoolean open) {
        long last = afterId;
        try {
            while (open.get()) {
                long seen = signal.version();
                List<RunEvent> batch = runs.events(id, last, BATCH);
                for (RunEvent event : batch) {
                    emitter.send(SseEmitter.event()
                            .id(Long.toString(event.id()))
                            .name(event.type())
                            .data(event, MediaType.APPLICATION_JSON));
                    last = event.id();
                    if (event.isFinal()) {
                        emitter.complete();
                        return;
                    }
                }
                if (batch.size() == BATCH) {
                    continue;
                }
                if (!signal.awaitChange(seen, HEARTBEAT)) {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                }
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE client for run {} went away: {}", id, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (RuntimeException e) {
            log.warn("SSE stream for run {} failed", id, e);
            emitter.completeWithError(e);
        }
    }
}
