package dev.bootshift.sandbox;

import java.util.Map;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;

import org.junit.jupiter.api.Assumptions;
import org.springframework.util.unit.DataSize;

/** Connects to Docker for sandbox tests, or skips them when Docker or the image is missing. */
public final class SandboxTestSupport {

    private SandboxTestSupport() {
    }

    /** In CI ({@code CI=true}) a missing Docker or image fails instead of skipping. */
    public static DockerClient dockerOrSkip(String image) {
        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        DockerClient docker = SandboxConfig.createClient(new SandboxProperties(image, "unused", "unused",
                DataSize.ofGigabytes(1), 1, 1, DataSize.ofMegabytes(1), java.time.Duration.ofMinutes(1), DataSize.ofMegabytes(1), null));
        try {
            docker.pingCmd().exec();
        } catch (RuntimeException e) {
            if (ci) {
                throw new IllegalStateException("Docker is required for sandbox tests in CI", e);
            }
            Assumptions.abort("Docker is not available: " + e.getMessage());
        }
        try {
            docker.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            if (ci) {
                throw new IllegalStateException("Build the sandbox image first: docker build -t " + image + " sandbox", e);
            }
            Assumptions.abort("Sandbox image " + image + " not built; run: docker build -t " + image + " sandbox");
        }
        return docker;
    }

    public static void ensureInternalNetwork(DockerClient docker, String name) {
        try {
            docker.inspectNetworkCmd().withNetworkId(name).exec();
        } catch (NotFoundException e) {
            docker.createNetworkCmd().withName(name).withInternal(true)
                    .withLabels(Map.of("dev.bootshift.test", "true")).exec();
        }
    }
}
