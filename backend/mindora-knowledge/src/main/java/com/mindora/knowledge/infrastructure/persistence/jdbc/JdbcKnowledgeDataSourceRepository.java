package com.mindora.knowledge.infrastructure.persistence.jdbc;

import com.mindora.common.id.PublicIds;
import com.mindora.knowledge.application.port.KnowledgeRepositories;
import com.mindora.knowledge.domain.DataSource;
import com.mindora.knowledge.domain.KnowledgeEnums;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public class JdbcKnowledgeDataSourceRepository implements KnowledgeRepositories.DataSourceRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeDataSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DataSource save(DataSource source) {
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_data_source (
                    id, name, source_type, repository_url, branch_name, access_token,
                    root_path, default_visibility, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    repository_url = VALUES(repository_url),
                    branch_name = VALUES(branch_name),
                    access_token = VALUES(access_token),
                    root_path = VALUES(root_path),
                    default_visibility = VALUES(default_visibility),
                    enabled = VALUES(enabled),
                    updated_at = VALUES(updated_at)
                """,
                PublicIds.toPublicId(source.id()),
                source.name(),
                source.sourceType().name().toLowerCase(),
                source.repositoryUrl(),
                source.branch(),
                source.accessToken(),
                source.rootPath(),
                source.defaultVisibility(),
                source.enabled(),
                timestamp(source.createdAt()),
                timestamp(source.updatedAt()));
        return source;
    }

    @Override
    public Optional<DataSource> find(UUID id) {
        return jdbcTemplate.query(
                        "SELECT * FROM knowledge_data_source WHERE id = ?",
                        (rs, rowNum) -> map(rs),
                        PublicIds.toPublicId(id))
                .stream()
                .findFirst();
    }

    @Override
    public List<DataSource> list() {
        return jdbcTemplate.query(
                "SELECT * FROM knowledge_data_source ORDER BY updated_at DESC",
                (rs, rowNum) -> map(rs));
    }

    private DataSource map(ResultSet rs) throws SQLException {
        return new DataSource(
                PublicIds.toUuid(rs.getString("id")),
                rs.getString("name"),
                KnowledgeEnums.SourceType.valueOf(rs.getString("source_type").toUpperCase()),
                rs.getString("repository_url"),
                rs.getString("branch_name"),
                rs.getString("access_token"),
                rs.getString("root_path"),
                rs.getString("default_visibility"),
                rs.getBoolean("enabled"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("updated_at")));
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
