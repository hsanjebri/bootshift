package dev.bootshift.github;

import java.net.URI;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code bootshift.github.*}. Each install creates its own GitHub App (README section 18).
 *
 * @param appId          numeric App ID from the app's settings page
 * @param appSlug        the app's URL name, as in github.com/apps/{slug}; used for the install link
 * @param privateKeyPath the .pem private key generated for the app; keep it outside the repository
 * @param baseUrl        GitHub REST API root; change only for GitHub Enterprise
 */
@ConfigurationProperties("bootshift.github")
public record GitHubProperties(
        @Nullable Long appId,
        @Nullable String appSlug,
        @Nullable Path privateKeyPath,
        @DefaultValue("https://api.github.com") URI baseUrl) {

    public GitHubProperties {
        if (!"https".equals(baseUrl.getScheme())) {
            throw new IllegalArgumentException("bootshift.github.base-url must use https: " + baseUrl);
        }
    }

    /** True when an App ID and a private key are configured. */
    public boolean configured() {
        return appId != null && privateKeyPath != null;
    }

    /** Where a user installs the app on their account or repositories. */
    public @Nullable String installUrl() {
        return appSlug == null || appSlug.isBlank() ? null : "https://github.com/apps/" + appSlug + "/installations/new";
    }
}
