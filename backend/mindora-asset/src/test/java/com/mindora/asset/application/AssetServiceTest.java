package com.mindora.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mindora.asset.domain.Asset;
import com.mindora.asset.infrastructure.persistence.filesystem.FileSystemAssetRepository;
import com.mindora.asset.infrastructure.persistence.memory.InMemoryAssetRepository;
import com.mindora.common.exception.BusinessException;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetServiceTest {
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3, 4
    };

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(new InMemoryAssetRepository());
    }

    @Test
    void uploadsAndListsBlogCoverAsset() {
        Asset asset = assetService.upload(
                "cover.png",
                "image/png",
                "blog_cover",
                PNG_BYTES.length,
                new ByteArrayInputStream(PNG_BYTES));

        assertEquals("blog_cover", asset.assetType());
        assertEquals("image/png", asset.mimeType());
        assertEquals(PNG_BYTES.length, asset.size());
        assertEquals(1, assetService.list().size());
    }

    @Test
    void rejectsUnsupportedOrSpoofedAssetContent() {
        BusinessException unsupported = assertThrows(
                BusinessException.class,
                () -> assetService.upload(
                        "notes.svg",
                        "image/svg+xml",
                        "blog_cover",
                        11,
                        new ByteArrayInputStream("<svg></svg>".getBytes())));
        assertEquals("asset_type_unsupported", unsupported.code());

        BusinessException spoofed = assertThrows(
                BusinessException.class,
                () -> assetService.upload(
                        "cover.png",
                        "image/png",
                        "blog_cover",
                        8,
                        new ByteArrayInputStream("not-image".getBytes())));
        assertEquals("asset_type_unsupported", spoofed.code());
    }

    @Test
    void rejectsOversizedAssetsBeforeReadingContent() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> assetService.upload(
                        "cover.png",
                        "image/png",
                        "blog_cover",
                        AssetService.MAX_ASSET_SIZE_BYTES + 1,
                        new ByteArrayInputStream(PNG_BYTES)));

        assertEquals("asset_too_large", exception.code());
    }

    @Test
    void fileSystemRepositoryKeepsAssetsAcrossServiceInstances(@TempDir Path storageRoot) {
        AssetService firstService = new AssetService(new FileSystemAssetRepository(storageRoot));
        Asset asset = firstService.upload(
                "cover.png",
                "image/png",
                "blog_cover",
                PNG_BYTES.length,
                new ByteArrayInputStream(PNG_BYTES));

        AssetService secondService = new AssetService(new FileSystemAssetRepository(storageRoot));

        assertTrue(secondService.find(asset.id()).isPresent());
        assertEquals(PNG_BYTES.length, secondService.content(asset.id()).length);
    }
}
