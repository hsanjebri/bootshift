package dev.bootshift.repo;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

    private final JdbcClient jdbc;

    public RepositoryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
}
