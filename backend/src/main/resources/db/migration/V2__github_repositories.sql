-- GitHub details for allowlisted repositories (Phase 5, pulled forward).
ALTER TABLE repository
    ADD COLUMN github_id       BIGINT,
    ADD COLUMN installation_id BIGINT,
    ADD COLUMN is_private      BOOLEAN,
    ADD COLUMN html_url        TEXT,
    ADD COLUMN allowed_at      TIMESTAMPTZ;

-- GitHub names are case-insensitive; look them up that way.
CREATE INDEX repository_lower_name_idx ON repository (provider, lower(owner), lower(name));
CREATE INDEX repository_allowed_idx ON repository (allowed) WHERE allowed;
