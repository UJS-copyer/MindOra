package com.mindora.asset.application;

import com.mindora.asset.domain.Asset;
import com.mindora.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JdbcAssetRepository implements AssetRepository {
    private final JdbcTemplate jdbcTemplate;
    private final Path contentDirectory;

    public JdbcAssetRepository(JdbcTemplate jdbcTemplate, Path storageRoot) {
        this.jdbcTemplate = jdbcTemplate;
        this.contentDirectory = storageRoot.resolve("content");
        try {
            Files.createDirectories(contentDirectory);
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
    }

    @Override
    @Transactional
    public Asset save(Asset asset, InputStream content) throws IOException {
        Path path = contentPath(asset.id());
        try (OutputStream output = Files.newOutputStream(path)) {
            content.transferTo(output);
        }
        jdbcTemplate.update(
                """
                INSERT INTO asset (
                    id, file_name, mime_type, size_bytes, asset_type,
                    storage_path, public_url, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    file_name = VALUES(file_name),
                    mime_type = VALUES(mime_type),
                    size_bytes = VALUES(size_bytes),
                    asset_type = VALUES(asset_type),
                    storage_path = VALUES(storage_path),
                    public_url = VALUES(public_url),
                    deleted = FALSE
                """,
                publicId(asset.id()),
                asset.fileName(),
                asset.mimeType(),
                asset.size(),
                asset.assetType(),
                path.toString(),
                asset.publicUrl(),
                asset.createdAt());
        return asset;
    }

    @Override
    public Optional<Asset> find(UUID id) {
        return jdbcTemplate.query(
                        """
                        SELECT id, file_name, mime_type, size_bytes, asset_type, public_url, created_at
                        FROM asset
                        WHERE id = ? AND deleted = FALSE
                        """,
                        this::mapAsset,
                        publicId(id))
                .stream()
                .findFirst();
    }

    @Override
    public Optional<byte[]> content(UUID id) {
        Path path = contentPath(id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
    }

    @Override
    public List<Asset> list() {
        return jdbcTemplate.query(
                """
                SELECT id, file_name, mime_type, size_bytes, asset_type, public_url, created_at
                FROM asset
                WHERE deleted = FALSE
                ORDER BY created_at DESC
                """,
                this::mapAsset);
    }

    @Override
    public void delete(UUID id) throws IOException {
        jdbcTemplate.update("UPDATE asset SET deleted = TRUE WHERE id = ?", publicId(id));
        Files.deleteIfExists(contentPath(id));
    }

    private Asset mapAsset(ResultSet rs, int rowNum) throws SQLException {
        return new Asset(
                uuid(rs.getString("id")),
                rs.getString("file_name"),
                rs.getString("mime_type"),
                rs.getLong("size_bytes"),
                rs.getString("asset_type"),
                rs.getString("public_url"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Path contentPath(UUID id) {
        return contentDirectory.resolve(publicId(id) + ".bin");
    }

    private String publicId(UUID id) {
        return id.toString().replace("-", "");
    }

    private UUID uuid(String id) {
        return UUID.fromString(
                id.substring(0, 8)
                        + "-"
                        + id.substring(8, 12)
                        + "-"
                        + id.substring(12, 16)
                        + "-"
                        + id.substring(16, 20)
                        + "-"
                        + id.substring(20));
    }
}
