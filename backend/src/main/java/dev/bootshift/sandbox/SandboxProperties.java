package dev.bootshift.sandbox;

import java.time.Duration;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * {@code bootshift.sandbox.*}: limits for every build container (README sections 13 and 18).
 *
 * @param image        image built from {@code sandbox/Dockerfile}
 * @param network      Docker network for build containers; must be internal (no route out)
 * @param mavenMirror  the dependency proxy, as seen from inside that network
 * @param memory       memory limit; swap is disabled
 * @param cpus         CPU limit
 * @param pidsLimit    maximum processes and threads
 * @param tmpSize      size of the {@code /tmp} tmpfs
 * @param buildTimeout a build that runs longer is killed
 * @param maxLogSize   log bytes kept per build; the rest is dropped and the log says so
 * @param dockerHost   Docker endpoint, for example {@code tcp://socket-proxy:2375}; defaults to
 *                     {@code DOCKER_HOST} or the platform's local socket
 */
@ConfigurationProperties("bootshift.sandbox")
public record SandboxProperties(
        @DefaultValue("bootshift-sandbox:dev") String image,
        @DefaultValue("bootshift-sandbox") String network,
        @DefaultValue("http://reposilite:8080/releases") String mavenMirror,
        @DefaultValue("4GB") DataSize memory,
        @DefaultValue("2") double cpus,
        @DefaultValue("512") long pidsLimit,
        @DefaultValue("1GB") DataSize tmpSize,
        @DefaultValue("20m") Duration buildTimeout,
        @DefaultValue("50MB") DataSize maxLogSize,
        @Nullable String dockerHost) {

    public SandboxProperties {
        if (cpus <= 0 || pidsLimit <= 0 || memory.toBytes() <= 0 || tmpSize.toBytes() <= 0) {
            throw new IllegalArgumentException("bootshift.sandbox limits must be positive");
        }
    }
}
