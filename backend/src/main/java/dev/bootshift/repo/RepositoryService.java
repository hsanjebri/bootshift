package dev.bootshift.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Repositories Bootshift knows about, and the allowlist that decides which ones it may touch. */
@Service
public class RepositoryService {

    private final JdbcClient jdbc;

    public RepositoryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** An allowlisted repository as stored. */
    public record AllowedRepository(String owner, String name, @Nullable Long githubId, @Nullable Long installationId,
                                    boolean isPrivate, @Nullable String htmlUrl, @Nullable Instant allowedAt) {
        public String fullName() {
            return owner + "/" + name;
        }
    }

    /** GitHub details recorded when a repository is allowed. */
    public record GitHubDetails(long githubId, long installationId, boolean isPrivate, String htmlUrl) {
    }

    /** Returns the id of the repository row, creating it (not allowed yet) if it does not exist. */
    public UUID findOrCreate(RepositoryName repository) {
        jdbc.sql("""
                INSERT INTO repository (id, provider, owner, name)
                VALUES (:id, 'github', :owner, :name)
                ON CONFLICT (provider, owner, name) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("owner", repository.owner())
                .param("name", repository.name())
                .update();
        return jdbc.sql("SELECT id FROM repository WHERE provider = 'github' AND owner = :owner AND name = :name")
                .param("owner", repository.owner())
                .param("name", repository.name())
                .query(UUID.class)
                .single();
    }

    public boolean isAllowed(RepositoryName repository) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM repository
                               WHERE provider = 'github' AND lower(owner) = lower(:owner) AND lower(name) = lower(:name)
                                 AND allowed)
                """)
                .param("owner", repository.owner())
                .param("name", repository.name())
                .query(Boolean.class)
                .single();
    }

    public List<AllowedRepository> allowed() {
        return jdbc.sql("""
                SELECT owner, name, github_id, installation_id, coalesce(is_private, FALSE) AS is_private, html_url, allowed_at
                FROM repository WHERE provider = 'github' AND allowed
                ORDER BY lower(owner), lower(name)
                """)
                .query(this::mapAllowed)
                .list();
    }

    /** Allows the repository and records its GitHub details. */
    @Transactional
    public void allow(RepositoryName repository, GitHubDetails details) {
        findOrCreate(repository);
        jdbc.sql("""
                UPDATE repository
                SET allowed = TRUE, allowed_at = now(), github_id = :githubId, installation_id = :installationId,
                    is_private = :isPrivate, html_url = :htmlUrl
                WHERE provider = 'github' AND owner = :owner AND name = :name
                """)
                .param("githubId", details.githubId())
                .param("installationId", details.installationId())
                .param("isPrivate", details.isPrivate())
                .param("htmlUrl", details.htmlUrl())
                .param("owner", repository.owner())
                .param("name", repository.name())
                .update();
    }

    /** Removes the repository from the allowlist. Past runs are kept. */
    public void disallow(RepositoryName repository) {
        jdbc.sql("""
                UPDATE repository SET allowed = FALSE, allowed_at = NULL
                WHERE provider = 'github' AND lower(owner) = lower(:owner) AND lower(name) = lower(:name)
                """)
                .param("owner", repository.owner())
                .param("name", repository.name())
                .update();
    }

    private AllowedRepository mapAllowed(ResultSet rs, int row) throws SQLException {
        Timestamp allowedAt = rs.getTimestamp("allowed_at");
        return new AllowedRepository(
                rs.getString("owner"),
                rs.getString("name"),
                rs.getObject("github_id", Long.class),
                rs.getObject("installation_id", Long.class),
                rs.getBoolean("is_private"),
                rs.getString("html_url"),
                allowedAt == null ? null : allowedAt.toInstant());
    }
}
