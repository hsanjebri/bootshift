package dev.bootshift.github;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The few GitHub REST endpoints Bootshift needs, as a Spring HTTP interface client. Every call
 * passes its own Authorization header: app endpoints take the app JWT, installation endpoints an
 * installation token.
 */
@HttpExchange(accept = "application/vnd.github+json")
public interface GitHubApi {

    @GetExchange("/app/installations")
    List<Installation> installations(@RequestHeader("Authorization") String bearerJwt,
                                     @RequestParam("per_page") int perPage);

    @PostExchange("/app/installations/{id}/access_tokens")
    AccessToken createInstallationToken(@RequestHeader("Authorization") String bearerJwt, @PathVariable long id);

    /** A token limited to some repositories and permissions; never cached. */
    @PostExchange("/app/installations/{id}/access_tokens")
    AccessToken createScopedToken(@RequestHeader("Authorization") String bearerJwt, @PathVariable long id,
                                  @RequestBody TokenScope scope);

    @GetExchange("/installation/repositories")
    InstallationRepositories repositories(@RequestHeader("Authorization") String bearerToken,
                                          @RequestParam("per_page") int perPage,
                                          @RequestParam int page);

    /**
     * Finishes the manifest flow: GitHub creates the app and returns its ID and private key once.
     * The one-hour code from the redirect is the only authorization.
     */
    @PostExchange("/app-manifests/{code}/conversions")
    ManifestConversion convertManifest(@PathVariable String code);

    /** Body of a scoped token request, for example one repository with {@code contents: read}. */
    record TokenScope(List<String> repositories, Map<String, String> permissions) {
    }

    /** The created app. Client and webhook secrets are not read; Bootshift does not use them. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ManifestConversion(long id, String slug, String name, @JsonProperty("html_url") String htmlUrl,
                              Account owner, String pem) {

        @Override
        public String toString() {
            return "ManifestConversion[id=" + id + ", slug=" + slug + ", owner=" + owner.login() + ", pem=<hidden>]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Installation(long id, Account account, @JsonProperty("repository_selection") String repositorySelection) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(String login, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccessToken(String token, @JsonProperty("expires_at") Instant expiresAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InstallationRepositories(@JsonProperty("total_count") int totalCount, List<Repo> repositories) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Repo(
            long id,
            String name,
            @JsonProperty("full_name") String fullName,
            Account owner,
            @JsonProperty("private") boolean isPrivate,
            boolean archived,
            @JsonProperty("default_branch") String defaultBranch,
            @JsonProperty("html_url") String htmlUrl,
            @JsonProperty("pushed_at") @Nullable Instant pushedAt) {
    }
}
