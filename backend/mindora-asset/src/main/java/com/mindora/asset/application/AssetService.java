package com.mindora.asset.application;

import com.mindora.asset.domain.Asset;
import com.mindora.asset.domain.AssetRepository;
import com.mindora.common.exception.BusinessException;
import com.mindora.common.id.PublicIds;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class AssetService {
    public static final long MAX_ASSET_SIZE_BYTES = 5L * 1024L * 1024L;

    private final AssetRepository repository;
    private final Clock clock;

    public AssetService(AssetRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public AssetService(AssetRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Asset upload(String fileName, String mimeType, String assetType, byte[] content) {
        return upload(
                fileName,
                mimeType,
                assetType,
                content == null ? 0 : content.length,
                new ByteArrayInputStream(content == null ? new byte[0] : content));
    }

    public Asset upload(
            String fileName,
            String mimeType,
            String assetType,
            long size,
            InputStream content) {
        if (fileName == null || fileName.isBlank() || content == null || size <= 0) {
            throw new BusinessException("asset_invalid", "Asset file is required");
        }
        if (size > MAX_ASSET_SIZE_BYTES) {
            throw new BusinessException("asset_too_large", "Asset file exceeds the 5 MB limit");
        }
        String normalizedMime = normalizeMime(mimeType);
        String normalizedType = assetType == null || assetType.isBlank() ? "admin_upload" : assetType;
        UUID id = UUID.randomUUID();
        Asset asset = new Asset(
                id,
                fileName.trim(),
                normalizedMime,
                size,
                normalizedType,
                "/api/v1/public/assets/" + PublicIds.toPublicId(id),
                Instant.now(clock));
        try {
            BufferedInputStream bufferedContent = new BufferedInputStream(content);
            bufferedContent.mark(16);
            byte[] header = bufferedContent.readNBytes(12);
            bufferedContent.reset();
            if (!isAllowedImage(normalizedMime, header)) {
                throw new BusinessException("asset_type_unsupported", "Only raster image assets are supported");
            }
            return repository.save(asset, bufferedContent);
        } catch (IOException exception) {
            throw new BusinessException("asset_storage_error", "Asset storage failed");
        }
    }

    public List<Asset> list() {
        return repository.list();
    }

    public Optional<Asset> find(UUID id) {
        return repository.find(id);
    }

    public byte[] content(UUID id) {
        return repository.content(id)
                .orElseThrow(() -> new BusinessException("asset_not_found", "Asset not found"));
    }

    public void delete(UUID id) {
        throw new BusinessException("asset_delete_blocked", "Asset deletion requires reference tracking");
    }

    private String normalizeMime(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            throw new BusinessException("asset_type_unsupported", "Only raster image assets are supported");
        }
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "image/png", "image/jpeg", "image/gif", "image/webp" -> normalized;
            default -> throw new BusinessException(
                    "asset_type_unsupported", "Only raster image assets are supported");
        };
    }

    private boolean isAllowedImage(String mimeType, byte[] header) {
        return switch (mimeType) {
            case "image/png" -> startsWith(header, new byte[] {
                    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
            });
            case "image/jpeg" -> startsWith(header, new byte[] {
                    (byte) 0xff, (byte) 0xd8, (byte) 0xff
            });
            case "image/gif" -> startsWith(header, "GIF87a".getBytes())
                    || startsWith(header, "GIF89a".getBytes());
            case "image/webp" -> startsWith(header, "RIFF".getBytes())
                    && header.length >= 12
                    && startsWith(java.util.Arrays.copyOfRange(header, 8, 12), "WEBP".getBytes());
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
