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
        String visibility,
        Boolean knowledgeEnabled) {
    public ArticleDraftCommand {
        tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
    }

    public ArticleDraftCommand(
            String title,
            String slug,
            String summary,
            String body,
            UUID coverAssetId,
            UUID categoryId,
            Set<UUID> tagIds,
            String visibility) {
        this(title, slug, summary, body, coverAssetId, categoryId, tagIds, visibility, true);
    }

    public boolean knowledgeEnabledOrDefault() {
        return knowledgeEnabled == null || knowledgeEnabled;
    }
}
