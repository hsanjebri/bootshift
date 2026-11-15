package dev.bootshift.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.github.dockerjava.api.DockerClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

/**
 * Proves the sandbox settings from README section 13 on a real Docker engine: no internet, no
 * secrets, non-root, read-only root, limits, timeouts and cleanup.
 *
 * <p>Needs Docker and the sandbox image ({@code docker build -t bootshift-sandbox:dev sandbox}).
 * Skipped when either is missing, except in CI, where a missing image fails the build.
 */
@Tag("docker")
class DockerSandboxTest {

    static final String IMAGE = System.getProperty("bootshift.sandbox.image", "bootshift-sandbox:dev");
    static final String NETWORK = "bootshift-sandbox-test";

    static DockerClient docker;

    @TempDir
    Path workspace;

    final UUID runId = UUID.randomUUID();
    DockerSandbox sandbox;

    @BeforeAll
    static void connect() {
        docker = SandboxTestSupport.dockerOrSkip(IMAGE);
        SandboxTestSupport.ensureInternalNetwork(docker, NETWORK);
    }

    @AfterAll
    static void disconnect() throws IOException {
        if (docker != null) {
            docker.close();
        }
    }

    @AfterEach
    void releaseCache() {
        if (sandbox != null) {
            sandbox.release(runId);
        }
    }

    @Test
    void runsAsAnUnprivilegedUserOnAReadOnlyRoot() throws Exception {
        Output out = run(properties(), "id -u; id -g; touch /etc/x 2>/dev/null && echo root-writable || echo root-read-only;"
                + " echo hi > /workspace/probe && echo workspace-writable; echo hi > /m2/probe && echo cache-writable;"
                + " echo hi > /tmp/probe && echo tmp-writable; grep CapEff /proc/self/status");

        assertThat(out.result().succeeded()).isTrue();
        assertThat(out.lines()).startsWith("1000", "1000", "root-read-only", "workspace-writable", "cache-writable", "tmp-writable");
        assertThat(out.lines()).contains("CapEff:\t0000000000000000");
        assertThat(workspace.resolve("probe")).hasContent("hi");
    }

    @Test
    void cannotReachTheInternet() throws Exception {
        Output out = run(properties(), "for target in 1.1.1.1/443 8.8.8.8/53 140.82.112.3/443; do"
                + " timeout 5 bash -c \"exec 3<>/dev/tcp/$target\" 2>/dev/null && echo \"open $target\" || echo \"blocked $target\"; done;"
                + " getent hosts github.com >/dev/null && echo dns-resolves || echo dns-blocked");

        assertThat(out.lines()).containsExactly(
                "blocked 1.1.1.1/443", "blocked 8.8.8.8/53", "blocked 140.82.112.3/443", "dns-blocked");
    }

    @Test
    void seesNoSecretsAndNoDockerSocket() throws Exception {
        Output out = run(properties(), "env | cut -d= -f1 | sort; test -e /var/run/docker.sock && echo socket || echo no-socket;"
                + " cat /proc/1/mountinfo | awk '{print $5}' | sort");

        Set<String> names = out.lines().stream().takeWhile(l -> !l.endsWith("socket")).collect(Collectors.toSet());
        // Only what DockerSandbox sets plus the image's own defaults; nothing from the server's environment.
        assertThat(names).isSubsetOf("JAVA_HOME", "PATH", "HOME", "MAVEN_CONFIG", "BOOTSHIFT_MAVEN_MIRROR", "HOSTNAME",
                "LANG", "LANGUAGE", "LC_ALL", "JAVA_VERSION", "MAVEN_HOME", "USER_HOME_DIR", "PWD", "SHLVL", "_");
        assertThat(names).noneMatch(n -> n.matches("(?i).*(TOKEN|SECRET|PASSWORD|KEY|GITHUB|_DB_).*"));
        assertThat(out.lines()).contains("no-socket");
        assertThat(out.lines()).doesNotContain("/var/run/docker.sock", "/run/docker.sock");
    }

    @Test
    void appliesMemoryAndProcessLimits() throws Exception {
        SandboxProperties limited = properties(DataSize.ofMegabytes(512), Duration.ofMinutes(1), DataSize.ofMegabytes(5));
        // cgroup v2 (most Linux hosts, GitHub Actions) or v1 (Docker Desktop on WSL 2).
        Output out = run(limited, """
                if [ -f /sys/fs/cgroup/memory.max ]; then
                  echo memory=$(cat /sys/fs/cgroup/memory.max); echo pids=$(cat /sys/fs/cgroup/pids.max)
                  echo cpu=$(tr ' ' / < /sys/fs/cgroup/cpu.max)
                else
                  echo memory=$(cat /sys/fs/cgroup/memory/memory.limit_in_bytes); echo pids=$(cat /sys/fs/cgroup/pids/pids.max)
                  echo cpu=$(cat /sys/fs/cgroup/cpu/cpu.cfs_quota_us)/$(cat /sys/fs/cgroup/cpu/cpu.cfs_period_us)
                fi
                """);

        assertThat(out.lines()).containsExactly("memory=" + 512L * 1024 * 1024, "pids=512", "cpu=200000/100000");
    }

    @Test
    void killsABuildThatRunsTooLong() throws Exception {
        SandboxProperties quick = properties(DataSize.ofGigabytes(1), Duration.ofSeconds(3), DataSize.ofMegabytes(5));
        Output out = run(quick, "echo started; sleep 60; echo finished");

        assertThat(out.result().outcome()).isEqualTo(Sandbox.Outcome.TIMED_OUT);
        assertThat(out.result().duration()).isLessThan(Duration.ofSeconds(20));
        assertThat(out.lines()).contains("started").doesNotContain("finished");
        assertThat(Files.readString(out.result().logFile())).contains("[bootshift] Build killed after PT3S");
        assertThat(containersOfRun()).isEmpty();
    }

    @Test
    void killsABuildWhenTheRunIsCancelled() throws Exception {
        AtomicBoolean cancelled = new AtomicBoolean();
        sandbox = new DockerSandbox(docker, properties());
        List<String> lines = new ArrayList<>();
        Sandbox.Result result = sandbox.run(command("sleep 60"), line -> {
            lines.add(line);
        }, () -> {
            cancelled.set(true);
            return true;
        });

        assertThat(cancelled).isTrue();
        assertThat(result.outcome()).isEqualTo(Sandbox.Outcome.CANCELLED);
        assertThat(containersOfRun()).isEmpty();
    }

    @Test
    void capsTheLog() throws Exception {
        SandboxProperties small = properties(DataSize.ofGigabytes(1), Duration.ofMinutes(1), DataSize.ofKilobytes(10));
        Output out = run(small, "yes 'a line of build output' | head -c 100000");

        assertThat(out.result().logTruncated()).isTrue();
        assertThat(Files.size(out.result().logFile())).isLessThan(11 * 1024);
        assertThat(Files.readString(out.result().logFile())).endsWith("[bootshift] Log truncated after 10240 bytes\n");
    }

    @Test
    void refusesANetworkWithInternetAccess() {
        SandboxProperties open = new SandboxProperties(IMAGE, "bridge", "http://unused", DataSize.ofGigabytes(1), 1, 128,
                DataSize.ofMegabytes(64), Duration.ofMinutes(1), DataSize.ofMegabytes(5), null);
        sandbox = new DockerSandbox(docker, open);

        assertThatThrownBy(() -> sandbox.run(command("true"), l -> { }, () -> false))
                .isInstanceOf(Sandbox.SandboxUnavailable.class)
                .hasMessageContaining("is not internal");
    }

    @Test
    void keepsTheMavenCacheForTheWholeRunAndRemovesItOnRelease() throws Exception {
        run(properties(), "echo cached > /m2/marker");
        Output second = run(properties(), "cat /m2/marker");
        assertThat(second.lines()).containsExactly("cached");

        sandbox.release(runId);
        assertThat(docker.listVolumesCmd().withFilter("name", List.of(DockerSandbox.cacheName(runId))).exec().getVolumes())
                .isNullOrEmpty();
    }

    private record Output(Sandbox.Result result, List<String> lines) {
    }

    private Output run(SandboxProperties properties, String script) throws InterruptedException {
        sandbox = new DockerSandbox(docker, properties);
        List<String> lines = new ArrayList<>();
        Sandbox.Result result = sandbox.run(command(script), lines::add, () -> false);
        return new Output(result, lines);
    }

    private Sandbox.Command command(String script) {
        return new Sandbox.Command(runId, "test", workspace, 25, List.of("bash", "-c", script), Duration.ofMinutes(5),
                workspace.resolveSibling(workspace.getFileName() + "-logs").resolve("test.log"));
    }

    private List<?> containersOfRun() {
        return docker.listContainersCmd().withShowAll(true)
                .withLabelFilter(Map.of(DockerSandbox.RUN_LABEL, runId.toString())).exec();
    }

    static SandboxProperties properties() {
        return properties(DataSize.ofGigabytes(1), Duration.ofMinutes(2), DataSize.ofMegabytes(5));
    }

    static SandboxProperties properties(DataSize memory, Duration timeout, DataSize maxLog) {
        return new SandboxProperties(IMAGE, NETWORK, "http://reposilite:8080/releases", memory, 2, 512,
                DataSize.ofMegabytes(256), timeout, maxLog, null);
    }
}
