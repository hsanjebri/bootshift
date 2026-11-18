package dev.bootshift.sandbox;

import java.time.Duration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/** Docker-backed sandbox. Only created for the real pipeline, so fake mode needs no Docker. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "bootshift.pipeline.mode", havingValue = "real", matchIfMissing = true)
class SandboxConfig {

    @Bean(destroyMethod = "close")
    DockerClient dockerClient(SandboxProperties properties) {
        return createClient(properties);
    }

    @Bean
    DockerSandbox sandbox(DockerClient dockerClient, SandboxProperties properties) {
        return new DockerSandbox(dockerClient, properties);
    }

    @EventListener(ApplicationReadyEvent.class)
    void cleanUp(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(DockerSandbox.class).removeLeftovers();
    }

    /** Uses {@code bootshift.sandbox.docker-host}, else {@code DOCKER_HOST}, else the local socket or pipe. */
    static DockerClient createClient(SandboxProperties properties) {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (properties.dockerHost() != null && !properties.dockerHost().isBlank()) {
            builder.withDockerHost(properties.dockerHost());
        }
        DockerClientConfig config = builder.build();
        ZerodepDockerHttpClient http = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(10))
                .build();
        return DockerClientImpl.getInstance(config, http);
    }
}
