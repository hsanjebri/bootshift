package dev.bootshift.run;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import dev.bootshift.run.RunExceptions.RunCancelled;

/**
 * Polls the Postgres queue and executes runs on virtual threads, at most
 * {@code bootshift.runs.max-concurrent} at a time.
 */
@Component
class RunWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);
    private static final int MAX_REASON_LENGTH = 500;

    private final RunStore store;
    private final RunStateWriter writer;
    private final RunEventLog events;
    private final MigrationPipeline pipeline;
    private final RunProperties.Runs settings;
    private final Semaphore slots;

    private volatile boolean running;
    private @Nullable Thread dispatcher;
    private @Nullable ExecutorService executor;

    RunWorker(RunStore store, RunStateWriter writer, RunEventLog events, MigrationPipeline pipeline, RunProperties properties) {
        this.store = store;
        this.writer = writer;
        this.events = events;
        this.pipeline = pipeline;
        this.settings = properties.runs();
        this.slots = new Semaphore(settings.maxConcurrent());
    }

    @Override
    public void start() {
        if (!settings.workerEnabled()) {
            log.info("Run worker disabled (bootshift.runs.worker-enabled=false)");
            return;
        }
        for (UUID id : store.requeueInFlight()) {
            log.warn("Re-queued run {} that was in progress when the server stopped", id);
            events.append(id, RunEvent.RECOVERED, Map.of("message", "Server restarted; run re-queued"));
        }
        executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("bootshift-run-", 0).factory());
        running = true;
        dispatcher = Thread.ofVirtual().name("bootshift-run-dispatcher").start(this::dispatch);
    }

    @Override
    public void stop() {
        running = false;
        if (dispatcher != null) {
            dispatcher.interrupt();
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Some runs did not stop within 10s; they will be re-queued on next start");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void dispatch() {
        while (running) {
            try {
                slots.acquire();
                Optional<UUID> claimed;
                try {
                    claimed = writer.claimNext();
                } catch (RuntimeException e) {
                    slots.release();
                    log.error("Could not poll the run queue", e);
                    Thread.sleep(settings.pollInterval());
                    continue;
                }
                if (claimed.isEmpty()) {
                    slots.release();
                    Thread.sleep(settings.pollInterval());
                    continue;
                }
                UUID id = claimed.get();
                executor.execute(() -> {
                    try {
                        process(id);
                    } finally {
                        slots.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void process(UUID id) {
        Run run = store.findById(id).orElseThrow();
        RunContext context = new RunContext(id, run.repository(), run.branch(), RunState.CLONING, store, writer, events);
        try {
            pipeline.execute(context);
            if (!context.state().isTerminal()) {
                context.move(RunState.FAILED, "Pipeline finished without reaching a result");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Run {} interrupted by shutdown in {}", id, context.state());
        } catch (RunCancelled e) {
            context.move(RunState.CANCELLED, null);
        } catch (RuntimeException e) {
            log.error("Run {} failed in {}", id, context.state(), e);
            if (!context.state().isTerminal()) {
                context.move(RunState.FAILED, truncate(e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
    }

    private static String truncate(String reason) {
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH) + "…";
    }
}
