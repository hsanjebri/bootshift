package dev.bootshift.github;

import java.net.URI;
import java.nio.file.Path;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code bootshift.github.*}. Each install creates its own GitHub App (README section 18), either
 * with the Connect GitHub button (saved under {@code configDir}) or by hand with these properties.
 * Properties win over a saved connection.
 *
 * @param appId          numeric App ID from the app's settings page
 * @param appSlug        the app's URL name, as in github.com/apps/{slug}; used for the install link
 * @param privateKeyPath the .pem private key generated for the app; keep it outside the repository
 * @param baseUrl        GitHub REST API root; change only for GitHub Enterprise
 * @param configDir      where the Connect GitHub button saves the app's ID and private key
 * @param dashboardUrl   the dashboard's address as the browser sees it. Unset, only localhost
 *                       addresses are accepted when connecting
 */
@ConfigurationProperties("bootshift.github")
public record GitHubProperties(
        @Nullable Long appId,
        @Nullable String appSlug,
        @Nullable Path privateKeyPath,
        @DefaultValue("https://api.github.com") URI baseUrl,
        @Nullable Path configDir,
        @Nullable URI dashboardUrl) {

    public GitHubProperties {
        if (!"https".equals(baseUrl.getScheme())) {
            throw new IllegalArgumentException("bootshift.github.base-url must use https: " + baseUrl);
        }
    }

    /** True when an App ID and a private key are set as properties. */
    public boolean configured() {
        return appId != null && privateKeyPath != null;
    }

    /** {@code configDir}, or {@code ~/.bootshift}. */
    public Path resolvedConfigDir() {
        return configDir != null ? configDir : Path.of(System.getProperty("user.home"), ".bootshift");
    }

    /** Where a user installs an app on their account or repositories. */
    static @Nullable String installUrl(@Nullable String slug) {
        return slug == null || slug.isBlank() ? null : "https://github.com/apps/" + slug + "/installations/new";
    }
}
