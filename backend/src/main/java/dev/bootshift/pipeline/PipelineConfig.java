package dev.bootshift.pipeline;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.bootshift.build.MavenBuild;
import dev.bootshift.git.GitClone;
import dev.bootshift.sandbox.Sandbox;
import dev.bootshift.sandbox.SandboxProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "bootshift.pipeline.mode", havingValue = "real", matchIfMissing = true)
class PipelineConfig {

    @Bean
    MavenBuild mavenBuild(Sandbox sandbox, SandboxProperties properties) {
        return new MavenBuild(sandbox, properties);
    }

    @Bean
    GitClone gitClone() {
        return new GitClone(Duration.ofMinutes(5));
    }
}
