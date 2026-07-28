package com.mindora.asset.infrastructure.persistence.filesystem;

import com.mindora.asset.domain.Asset;
import com.mindora.asset.domain.AssetRepository;
import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

public class FileSystemAssetRepository implements AssetRepository {
    private final Path metadataDirectory;
    private final Path contentDirectory;

    public FileSystemAssetRepository(Path storageRoot) {
        this.metadataDirectory = storageRoot.resolve("metadata");
        this.contentDirectory = storageRoot.resolve("content");
        try {
            Files.createDirectories(metadataDirectory);
            Files.createDirectories(contentDirectory);
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
    }

    @Override
    public Asset save(Asset asset, InputStream content) throws IOException {
        Files.copy(content, contentPath(asset.id()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Properties properties = toProperties(asset);
        try (OutputStream output = Files.newOutputStream(metadataPath(asset.id()))) {
            properties.store(output, "MindOra asset metadata");
        }
        return asset;
    }

    @Override
    public Optional<Asset> find(UUID id) {
        Path path = metadataPath(id);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(readAsset(path));
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
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
        try (Stream<Path> files = Files.list(metadataDirectory)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .map(this::readAssetUnchecked)
                    .toList();
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
    }

    @Override
    public void delete(UUID id) throws IOException {
        Files.deleteIfExists(contentPath(id));
        Files.deleteIfExists(metadataPath(id));
    }

    private Path metadataPath(UUID id) {
        return metadataDirectory.resolve(PublicIds.toPublicId(id) + ".properties");
    }

    private Path contentPath(UUID id) {
        return contentDirectory.resolve(PublicIds.toPublicId(id) + ".bin");
    }

    private Asset readAssetUnchecked(Path path) {
        try {
            return readAsset(path);
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
    }

    private Asset readAsset(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new Asset(
                UUID.fromString(properties.getProperty("id")),
                properties.getProperty("fileName"),
                properties.getProperty("mimeType"),
                Long.parseLong(properties.getProperty("size")),
                properties.getProperty("assetType"),
                properties.getProperty("publicUrl"),
                Instant.parse(properties.getProperty("createdAt")));
    }

    private Properties toProperties(Asset asset) {
        Properties properties = new Properties();
        properties.setProperty("id", asset.id().toString());
        properties.setProperty("fileName", asset.fileName());
        properties.setProperty("mimeType", asset.mimeType());
        properties.setProperty("size", Long.toString(asset.size()));
        properties.setProperty("assetType", asset.assetType());
        properties.setProperty("publicUrl", asset.publicUrl());
        properties.setProperty("createdAt", asset.createdAt().toString());
        return properties;
    }
}
