package com.mindora.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record Asset(
        UUID id,
        String fileName,
        String mimeType,
        long size,
        String assetType,
        String publicUrl,
        Instant createdAt) {
}
