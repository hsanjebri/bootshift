package dev.bootshift.github;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

import dev.bootshift.repo.AllowlistPolicy;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = GitHubConfig.GROUP, types = GitHubApi.class)
class GitHubConfig {

    static final String GROUP = "github";
    /** Pin the REST API version so GitHub changes cannot silently alter responses. */
    static final String API_VERSION = "2022-11-28";

    @Bean
    RestClientHttpServiceGroupConfigurer gitHubClientConfigurer(GitHubProperties properties) {
        return groups -> groups.filterByName(GROUP).forEachClient((group, builder) -> builder
                .baseUrl(properties.baseUrl().toString())
                .defaultHeader("X-GitHub-Api-Version", API_VERSION)
                .defaultHeader("User-Agent", "bootshift"));
    }

    /** Once a GitHub App is connected, runs are limited to allowlisted repositories. */
    @Bean
    AllowlistPolicy allowlistPolicy(GitHubCredentials credentials) {
        return credentials::configured;
    }
}
