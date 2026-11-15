package dev.bootshift.sandbox;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.Network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Sandbox} on Docker, with the container settings from README section 13. Every limit
 * comes from {@link SandboxProperties}; nothing in a {@link Sandbox.Command} can widen them.
 *
 * <p>Before each build it checks that the configured network is internal, so a misconfigured
 * install fails closed instead of giving builds internet access.
 */
public class DockerSandbox implements Sandbox {

    static final String LABEL = "dev.bootshift.sandbox";
    static final String RUN_LABEL = "dev.bootshift.run";

    private static final Logger log = LoggerFactory.getLogger(DockerSandbox.class);
    private static final Duration POLL = Duration.ofSeconds(1);
    private static final Duration LOG_DRAIN = Duration.ofSeconds(5);
    private static final String PATH = "/usr/share/maven/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

    private final DockerClient docker;
    private final SandboxProperties properties;

    public DockerSandbox(DockerClient docker, SandboxProperties properties) {
        this.docker = docker;
        this.properties = properties;
    }

    @Override
    public Result run(Command command, Consumer<String> lines, BooleanSupplier cancelled) throws InterruptedException {
        requireInternalNetwork();
        String cache = ensureCache(command.runId());
        Duration timeout = command.timeout().compareTo(properties.buildTimeout()) < 0 ? command.timeout() : properties.buildTimeout();

        long started = System.nanoTime();
        String id = create(command, cache);
        LogCollector collector = null;
        try {
            collector = new LogCollector(command, lines, properties.maxLogSize().toBytes());
            docker.startContainerCmd(id).exec();
            docker.logContainerCmd(id).withStdOut(true).withStdErr(true).withFollowStream(true).exec(collector);

            Outcome outcome = await(id, started, timeout, cancelled);
            if (outcome != Outcome.EXITED) {
                kill(id);
            }
            collector.awaitCompletion(LOG_DRAIN.toMillis(), TimeUnit.MILLISECONDS);
            int exitCode = exitCode(id);
            Duration duration = Duration.ofNanos(System.nanoTime() - started);
            collector.finish(outcome, timeout);
            return new Result(outcome, exitCode, duration, command.logFile(), collector.truncated());
        } catch (IOException e) {
            throw new SandboxUnavailable("Could not write the build log " + command.logFile(), e);
        } catch (DockerException e) {
            throw new SandboxUnavailable("Docker failed while running " + command.name() + ": " + e.getMessage(), e);
        } finally {
            if (collector != null) {
                collector.closeQuietly();
            }
            remove(id);
        }
    }

    @Override
    public void release(UUID runId) {
        try {
            docker.removeVolumeCmd(cacheName(runId)).exec();
        } catch (NotFoundException e) {
            // Never created, for example when the run stopped before its first build.
        } catch (DockerException e) {
            log.warn("Could not remove the Maven cache of run {}: {}", runId, e.getMessage());
        }
    }

    /**
     * Removes containers and caches a previous server process left behind, for example after a
     * crash during a build. Interrupted runs are re-queued and start from scratch.
     */
    public void removeLeftovers() {
        try {
            for (var container : docker.listContainersCmd().withShowAll(true).withLabelFilter(Map.of(LABEL, "true")).exec()) {
                log.warn("Removing leftover sandbox container {}", container.getId());
                remove(container.getId());
            }
            var volumes = docker.listVolumesCmd().withFilter("label", List.of(LABEL + "=true")).exec().getVolumes();
            for (var volume : volumes == null ? List.<com.github.dockerjava.api.command.InspectVolumeResponse>of() : volumes) {
                log.warn("Removing leftover sandbox cache {}", volume.getName());
                docker.removeVolumeCmd(volume.getName()).exec();
            }
        } catch (RuntimeException e) {
            log.warn("Could not clean up leftover sandbox containers: {}", e.getMessage());
        }
    }

    /** Fails closed: refuses to build if the network can reach anything but other containers on it. */
    void requireInternalNetwork() {
        Network network;
        try {
            network = docker.inspectNetworkCmd().withNetworkId(properties.network()).exec();
        } catch (NotFoundException e) {
            throw new SandboxUnavailable("Docker network '" + properties.network() + "' does not exist. "
                    + "Start it with: docker compose -f deploy/compose.dev.yaml up -d", e);
        } catch (RuntimeException e) {
            throw new SandboxUnavailable("Cannot reach Docker: " + e.getMessage(), e);
        }
        if (!Boolean.TRUE.equals(network.getInternal())) {
            throw new SandboxUnavailable("Docker network '" + properties.network()
                    + "' is not internal, so builds could reach the internet. Recreate it with internal: true.");
        }
    }

    private String ensureCache(UUID runId) {
        String name = cacheName(runId);
        try {
            docker.inspectVolumeCmd(name).exec();
        } catch (NotFoundException e) {
            docker.createVolumeCmd().withName(name).withLabels(Map.of(LABEL, "true", RUN_LABEL, runId.toString())).exec();
        }
        return name;
    }

    private String create(Command command, String cache) {
        HostConfig host = HostConfig.newHostConfig()
                .withMemory(properties.memory().toBytes())
                .withMemorySwap(properties.memory().toBytes())
                .withNanoCPUs((long) (properties.cpus() * 1_000_000_000L))
                .withPidsLimit(properties.pidsLimit())
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/tmp", "rw,exec,nosuid,nodev,size=" + properties.tmpSize().toBytes()))
                .withNetworkMode(properties.network())
                .withInit(true)
                .withMounts(List.of(
                        new Mount().withType(MountType.BIND).withSource(command.workspace().toAbsolutePath().toString()).withTarget("/workspace"),
                        new Mount().withType(MountType.VOLUME).withSource(cache).withTarget("/m2")));
        try {
            return docker.createContainerCmd(properties.image())
                    .withName("bootshift-" + command.runId().toString().substring(0, 8) + "-" + command.name() + "-" + shortId())
                    .withUser("1000:1000")
                    .withWorkingDir("/workspace")
                    .withEnv(environment(command.jdk()))
                    .withCmd(command.argv())
                    .withLabels(Map.of(LABEL, "true", RUN_LABEL, command.runId().toString()))
                    .withHostConfig(host)
                    .exec()
                    .getId();
        } catch (NotFoundException e) {
            throw new SandboxUnavailable("Sandbox image '" + properties.image() + "' not found. "
                    + "Build it with: docker build -t " + properties.image() + " sandbox", e);
        }
    }

    /**
     * The container's whole environment. Built from scratch: nothing from the server's own
     * environment (tokens, keys, database passwords) is passed through.
     */
    List<String> environment(int jdk) {
        String javaHome = "/opt/jdk/" + jdk;
        return List.of(
                "JAVA_HOME=" + javaHome,
                "PATH=" + javaHome + "/bin:" + PATH,
                "HOME=/tmp",
                "MAVEN_CONFIG=",
                "BOOTSHIFT_MAVEN_MIRROR=" + properties.mavenMirror());
    }

    /**
     * Polls the container state. A long-lived wait request can end early on an idle connection,
     * which would look like the build exited.
     */
    private Outcome await(String id, long started, Duration timeout, BooleanSupplier cancelled) throws InterruptedException {
        long deadline = started + timeout.toNanos();
        while (Boolean.TRUE.equals(docker.inspectContainerCmd(id).exec().getState().getRunning())) {
            if (cancelled.getAsBoolean()) {
                return Outcome.CANCELLED;
            }
            if (System.nanoTime() - deadline > 0) {
                return Outcome.TIMED_OUT;
            }
            Thread.sleep(POLL);
        }
        return Outcome.EXITED;
    }

    private int exitCode(String id) {
        Long code = docker.inspectContainerCmd(id).exec().getState().getExitCodeLong();
        return code == null ? -1 : code.intValue();
    }

    private void kill(String id) {
        try {
            docker.killContainerCmd(id).exec();
        } catch (DockerException e) {
            // Already stopped.
        }
    }

    /** Runs even when the thread was interrupted, so a shutdown never leaves a build running. */
    private void remove(String id) {
        boolean interrupted = Thread.interrupted();
        try {
            docker.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException e) {
            // Already gone.
        } catch (RuntimeException e) {
            log.warn("Could not remove sandbox container {}: {}", id, e.getMessage());
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static String cacheName(UUID runId) {
        return "bootshift-m2-" + runId;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Writes container output to the log file, up to a size cap, and hands out complete lines. */
    private static final class LogCollector extends ResultCallback.Adapter<Frame> {

        private final OutputStream out;
        private final Consumer<String> lines;
        private final long maxBytes;
        private final java.io.ByteArrayOutputStream partial = new java.io.ByteArrayOutputStream();
        private long written;
        private volatile boolean truncated;
        private boolean closed;

        LogCollector(Command command, Consumer<String> lines, long maxBytes) throws IOException {
            Files.createDirectories(command.logFile().getParent());
            this.out = Files.newOutputStream(command.logFile());
            this.lines = lines;
            this.maxBytes = maxBytes;
        }

        @Override
        public synchronized void onNext(Frame frame) {
            byte[] payload = frame.getPayload();
            if (payload == null || closed) {
                return;
            }
            try {
                if (written < maxBytes) {
                    int keep = (int) Math.min(payload.length, maxBytes - written);
                    out.write(payload, 0, keep);
                    written += keep;
                    if (keep < payload.length) {
                        truncated = true;
                    }
                } else {
                    truncated = true;
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            if (truncated) {
                return;
            }
            // Split on bytes, not chars: a frame can end in the middle of a multi-byte character.
            int start = 0;
            for (int i = 0; i < payload.length; i++) {
                if (payload[i] == '\n') {
                    partial.write(payload, start, i - start);
                    emit(partial.toString(StandardCharsets.UTF_8));
                    partial.reset();
                    start = i + 1;
                }
            }
            partial.write(payload, start, payload.length - start);
        }

        synchronized void finish(Outcome outcome, Duration timeout) throws IOException {
            if (partial.size() > 0) {
                emit(partial.toString(StandardCharsets.UTF_8));
                partial.reset();
            }
            List<String> notes = new ArrayList<>();
            if (truncated) {
                notes.add("[bootshift] Log truncated after " + maxBytes + " bytes");
            }
            if (outcome == Outcome.TIMED_OUT) {
                notes.add("[bootshift] Build killed after " + timeout);
            } else if (outcome == Outcome.CANCELLED) {
                notes.add("[bootshift] Build killed: run cancelled");
            }
            for (String note : notes) {
                out.write(("\n" + note + "\n").getBytes(StandardCharsets.UTF_8));
                lines.accept(note);
            }
            closed = true;
            out.close();
        }

        boolean truncated() {
            return truncated;
        }

        private void emit(String line) {
            lines.accept(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }

        /** Stops following the container output and closes the log file. Safe to call twice. */
        synchronized void closeQuietly() {
            closed = true;
            try {
                close();
            } catch (IOException e) {
                // The stream is gone either way.
            }
            try {
                out.close();
            } catch (IOException e) {
                // Nothing left to write.
            }
        }
    }
}
