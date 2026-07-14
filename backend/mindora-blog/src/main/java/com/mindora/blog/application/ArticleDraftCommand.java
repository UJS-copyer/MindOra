package com.mindora.blog.application;

import java.util.Set;
import java.util.UUID;

public record ArticleDraftCommand(
        String title,
        String slug,
        String summary,
        String body,
        UUID coverAssetId,
        UUID categoryId,
        Set<UUID> tagIds,
        String visibility) {
    public ArticleDraftCommand {
        tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
    }
}
